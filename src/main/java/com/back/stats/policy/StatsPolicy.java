package com.back.stats.policy;

import com.back.stats.policy.mapper.StatsPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 통계 수치의 단일 진입점 — 배치·기록기가 쓰는 모든 상수는 policy_settings 에서 읽는다.
 *
 * 코드에 남은 숫자는 "행이 없을 때의 기본값"뿐이다(하드코딩 금지 규칙). 따라서 정책 행을 넣지 않아도
 * 서비스는 기본값으로 동작하고, 운영 중 수치 조정은 DB 한 줄로 끝난다 — 재배포가 필요 없다.
 *
 * <p><b>어떤 이유로도 예외를 던지지 않는다.</b> 값이 비었든, 숫자가 아니든, DB 조회 자체가 실패했든
 * 경고만 남기고 기본값으로 되돌린다. 급상승 배치의 주기를 이 클래스가 정하는데(트리거가 매 실행마다 읽는다),
 * 여기서 예외가 나가면 트리거가 다음 실행 시각을 계산하지 못해 <b>스케줄이 영구히 멈춘다</b> —
 * DB 순단 한 번이 급상승 집계를 재기동 전까지 죽이는 셈이라, 정책 조회 실패는 기본값으로 흡수하는 쪽이 맞다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsPolicy {

    // --- 정책 코드 ---
    public static final String TRENDING_INTERVAL_MINUTES = "trending_interval_minutes";
    public static final String TRENDING_POST_MAX_AGE_HOURS = "trending_post_max_age_hours";
    public static final String TRENDING_MIN_DELTA_SCORE = "trending_min_delta_score";
    public static final String TRENDING_BASELINE_WINDOWS = "trending_baseline_windows";
    public static final String TRENDING_MIN_SCORE = "trending_min_score";
    public static final String TRENDING_SPIKE_RATIO = "trending_spike_ratio";
    public static final String TRENDING_TOP_N = "trending_top_n";
    public static final String TRENDING_WEIGHT_VIEW = "trending_weight_view";
    public static final String TRENDING_WEIGHT_LIKE = "trending_weight_like";
    public static final String TRENDING_WEIGHT_COMMENT = "trending_weight_comment";
    public static final String TRENDING_MIN_ACTIVE_USERS = "trending_min_active_users";
    public static final String TRENDING_HALFLIFE_HOURS = "trending_halflife_hours";
    public static final String SIGNUP_STATS_RECALC_WEEKS = "signup_stats_recalc_weeks";
    public static final String STATS_RETENTION_DAYS = "stats_retention_days";

    // --- 행이 없을 때의 기본값 ---
    private static final int DEFAULT_TRENDING_INTERVAL_MINUTES = 60;
    private static final int DEFAULT_TRENDING_POST_MAX_AGE_HOURS = 168;   // 7일
    private static final double DEFAULT_TRENDING_MIN_DELTA_SCORE = 5;     // 적재 컷
    private static final int DEFAULT_TRENDING_BASELINE_WINDOWS = 6;
    private static final double DEFAULT_TRENDING_MIN_SCORE = 10;          // 관문1
    private static final double DEFAULT_TRENDING_SPIKE_RATIO = 3.0;       // 관문2
    private static final int DEFAULT_TRENDING_TOP_N = 5;                  // 관문3
    private static final double DEFAULT_TRENDING_WEIGHT_VIEW = 1;         // 조회 가중치
    private static final double DEFAULT_TRENDING_WEIGHT_LIKE = 5;         // 좋아요 가중치
    private static final double DEFAULT_TRENDING_WEIGHT_COMMENT = 3;      // 댓글 가중치
    private static final int DEFAULT_TRENDING_MIN_ACTIVE_USERS = 5;       // 접속자수 하한 (새벽 소수 접속 보정)
    private static final int DEFAULT_TRENDING_HALFLIFE_HOURS = 24;        // 이 시간만큼 지나면 freshness 0.5
    private static final int DEFAULT_SIGNUP_STATS_RECALC_WEEKS = 2;
    private static final int DEFAULT_STATS_RETENTION_DAYS = 1825;         // 5년

    private final StatsPolicyMapper statsPolicyMapper;

    /**
     * 집계 주기(분). <b>60의 배수만 유효하다</b> — 접속자 수(점수의 분모)를 시간 버킷 테이블에서 세기 때문에
     * 30분·50분 같은 주기는 구간 경계가 정시와 어긋나 분모가 통째로 틀어진다.
     * 배수가 아닌 값은 경고만 남기고 60으로 되돌린다(잘못된 설정이 점수를 조용히 망치게 두지 않는다).
     */
    public int trendingIntervalMinutes() {
        int minutes = atLeastOne(TRENDING_INTERVAL_MINUTES, DEFAULT_TRENDING_INTERVAL_MINUTES);
        if (minutes % 60 != 0) {
            log.warn("정책 수치 {}={} 는 60의 배수가 아니라 접속자 수 집계 구간이 어긋난다 → 기본값 {} 로 대체",
                    TRENDING_INTERVAL_MINUTES, minutes, DEFAULT_TRENDING_INTERVAL_MINUTES);
            return DEFAULT_TRENDING_INTERVAL_MINUTES;
        }
        return minutes;
    }

    public int trendingPostMaxAgeHours() {
        return atLeastOne(TRENDING_POST_MAX_AGE_HOURS, DEFAULT_TRENDING_POST_MAX_AGE_HOURS);
    }

    public double trendingMinDeltaScore() {
        return getDouble(TRENDING_MIN_DELTA_SCORE, DEFAULT_TRENDING_MIN_DELTA_SCORE);
    }

    public int trendingBaselineWindows() {
        return atLeastOne(TRENDING_BASELINE_WINDOWS, DEFAULT_TRENDING_BASELINE_WINDOWS);
    }

    public double trendingMinScore() {
        return getDouble(TRENDING_MIN_SCORE, DEFAULT_TRENDING_MIN_SCORE);
    }

    public double trendingSpikeRatio() {
        return getDouble(TRENDING_SPIKE_RATIO, DEFAULT_TRENDING_SPIKE_RATIO);
    }

    public int trendingTopN() {
        return atLeastOne(TRENDING_TOP_N, DEFAULT_TRENDING_TOP_N);
    }

    public double trendingWeightView() {
        return getDouble(TRENDING_WEIGHT_VIEW, DEFAULT_TRENDING_WEIGHT_VIEW);
    }

    public double trendingWeightLike() {
        return getDouble(TRENDING_WEIGHT_LIKE, DEFAULT_TRENDING_WEIGHT_LIKE);
    }

    public double trendingWeightComment() {
        return getDouble(TRENDING_WEIGHT_COMMENT, DEFAULT_TRENDING_WEIGHT_COMMENT);
    }

    public int trendingMinActiveUsers() {
        return atLeastOne(TRENDING_MIN_ACTIVE_USERS, DEFAULT_TRENDING_MIN_ACTIVE_USERS);
    }

    public int trendingHalflifeHours() {
        return atLeastOne(TRENDING_HALFLIFE_HOURS, DEFAULT_TRENDING_HALFLIFE_HOURS);
    }

    public int signupStatsRecalcWeeks() {
        return atLeastOne(SIGNUP_STATS_RECALC_WEEKS, DEFAULT_SIGNUP_STATS_RECALC_WEEKS);
    }

    public int statsRetentionDays() {
        return atLeastOne(STATS_RETENTION_DAYS, DEFAULT_STATS_RETENTION_DAYS);
    }

    // 0·음수는 배치를 무한루프/전량삭제로 몰 수 있는 값이라 기본값으로 되돌린다
    private int atLeastOne(String code, int defaultValue) {
        int value = (int) getDouble(code, defaultValue);
        if (value < 1) {
            log.warn("정책 수치 {}={} 는 1 미만이라 기본값 {} 로 대체", code, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    // 조회(DB 순단·매퍼 오류)와 파싱 실패를 모두 흡수한다 — 스케줄 트리거가 이 값을 읽으므로
    // 예외를 밖으로 내보내면 다음 실행 시각이 계산되지 않아 배치가 되살아나지 못한다.
    private double getDouble(String code, double defaultValue) {
        String raw;
        try {
            raw = statsPolicyMapper.findPolicySetting(code);
        } catch (Exception e) {
            log.warn("정책 수치 {} 조회 실패 → 기본값 {} 사용 (원인={})", code, defaultValue, e.getMessage());
            return defaultValue;
        }
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("정책 수치 {}='{}' 를 숫자로 읽을 수 없어 기본값 {} 사용", code, raw, defaultValue);
            return defaultValue;
        }
    }
}
