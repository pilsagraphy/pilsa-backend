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
 * 값이 비었거나 숫자가 아니면 경고만 남기고 기본값으로 되돌린다(잘못된 설정이 배치를 멈추게 하지 않는다).
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
    public static final String TRENDING_ACTIVE_USER_FLOOR = "trending_active_user_floor";
    public static final String TRENDING_FRESHNESS_SCALE_HOURS = "trending_freshness_scale_hours";
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
    private static final int DEFAULT_TRENDING_ACTIVE_USER_FLOOR = 5;      // 접속자수 하한 (새벽 소수 접속 보정)
    private static final int DEFAULT_TRENDING_FRESHNESS_SCALE_HOURS = 24; // 이 시간만큼 지나면 freshness 0.5
    private static final int DEFAULT_SIGNUP_STATS_RECALC_WEEKS = 2;
    private static final int DEFAULT_STATS_RETENTION_DAYS = 1825;         // 5년

    private final StatsPolicyMapper statsPolicyMapper;

    public int trendingIntervalMinutes() {
        return atLeastOne(TRENDING_INTERVAL_MINUTES, DEFAULT_TRENDING_INTERVAL_MINUTES);
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

    public int trendingActiveUserFloor() {
        return atLeastOne(TRENDING_ACTIVE_USER_FLOOR, DEFAULT_TRENDING_ACTIVE_USER_FLOOR);
    }

    public int trendingFreshnessScaleHours() {
        return atLeastOne(TRENDING_FRESHNESS_SCALE_HOURS, DEFAULT_TRENDING_FRESHNESS_SCALE_HOURS);
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

    private double getDouble(String code, double defaultValue) {
        String raw = statsPolicyMapper.findPolicySetting(code);
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
