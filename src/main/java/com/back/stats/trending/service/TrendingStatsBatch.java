package com.back.stats.trending.service;

import com.back.stats.access.mapper.StatsAccessMapper;
import com.back.stats.policy.StatsPolicy;
import com.back.stats.trending.dto.PostStatHistory;
import com.back.stats.trending.dto.PostStatRow;
import com.back.stats.trending.dto.TrendingCandidate;
import com.back.stats.trending.mapper.StatsPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 급상승 집계 배치 — 한 구간의 게시글 활동을 집계해 stats_post_hourly 에 적재하고 급상승 여부를 판정한다.
 * 주기는 policy_settings.trending_interval_minutes (기본 60분). {@link TrendingScheduleConfig} 가 매 실행마다 다시 읽는다.
 *
 * <h3>집계 대상은 "직전에 끝난 구간"이다</h3>
 * 실행 시각이 속한 구간은 아직 진행 중이라 접속자 수(점수의 분모)가 덜 찬 상태다. 그래서 방금 닫힌 구간
 * [직전 구간 시작, 현재 구간 시작) 을 집계한다 — 분모가 완전한 구간이라야 구간끼리 점수를 비교할 수 있다.
 *
 * <h3>재실행 안전(멱등)</h3>
 * <ul>
 *   <li>기준선을 <b>구간 시작 이전 행</b>에서만 읽는다 → 같은 구간을 다시 돌려도 자기 행이 기준선이 되지 않는다.</li>
 *   <li>후보 조건(글 나이·작성 시점)을 wall clock 이 아니라 <b>구간 종료 시각</b>으로 판정한다 → 후보 집합이 흔들리지 않는다.</li>
 *   <li>적재는 PK(stat_hour, post_id) upsert → 행이 늘지 않고 값만 갱신된다.</li>
 *   <li>동점 정렬을 post_id 로 확정한다 → 순위가 실행마다 바뀌지 않는다.</li>
 * </ul>
 *
 * <h3>적재 컷을 두는 이유</h3>
 * raw_score 가 trending_min_delta_score 미달인 구간은 <b>행을 만들지 않는다</b>. 누적값을 행에 저장하므로
 * 건너뛴 증가분은 다음 적재 행에서 한꺼번에 잡히고(손실 없음), 조용한 글이 매 구간 행을 쌓는 일만 사라진다.
 *
 * <p>급상승 알림은 1차 범위가 아니다(PM 확정) — 이 배치는 notifications 를 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendingStatsBatch {

    // 한 번의 INSERT 에 담는 최대 행 수 (max_allowed_packet 여유)
    private static final int UPSERT_CHUNK_SIZE = 500;

    private final StatsPostMapper statsPostMapper;
    private final StatsAccessMapper statsAccessMapper;
    private final StatsPolicy statsPolicy;

    /**
     * 스케줄 진입점 — 직전에 끝난 구간을 집계한다.
     *
     * 트랜잭션을 여기에도 붙이는 이유: 아래 {@code aggregateBucket} 은 같은 빈의 메서드라 내부 호출로는
     * 프록시를 타지 않는다. 스케줄러가 프록시로 부르는 이 메서드에 경계가 있어야 실제로 한 트랜잭션이 된다.
     */
    @Transactional
    public void aggregateLatestBucket() {
        int intervalMinutes = statsPolicy.trendingIntervalMinutes();
        LocalDateTime currentBucketStart = truncateToInterval(LocalDateTime.now(), intervalMinutes);
        aggregateBucket(currentBucketStart.minusMinutes(intervalMinutes), currentBucketStart);
    }

    /**
     * 지정 구간 집계 — 누락 구간을 손으로 메울 때도 같은 경로를 쓴다(같은 구간 재실행 시 결과 동일).
     *
     * @param bucketStart 구간 시작 = stats_post_hourly.stat_hour
     * @param bucketEnd   구간 종료(미포함)
     */
    @Transactional
    public void aggregateBucket(LocalDateTime bucketStart, LocalDateTime bucketEnd) {
        int maxAgeHours = statsPolicy.trendingPostMaxAgeHours();

        List<TrendingCandidate> candidates = statsPostMapper.findCandidates(bucketEnd, maxAgeHours);
        if (candidates.isEmpty()) {
            log.info("급상승 집계 - 구간 {} 후보 없음 (최근 {}시간 글 기준)", bucketStart, maxAgeHours);
            return;
        }

        int windows = statsPolicy.trendingBaselineWindows();
        Map<Long, PostStatHistory> histories = loadHistories(candidates, bucketStart, windows);
        int denominator = Math.max(countActiveUsers(bucketStart, bucketEnd), statsPolicy.trendingActiveUserFloor());

        List<PostStatRow> rows = buildRows(candidates, histories, bucketStart, bucketEnd, windows, denominator);
        if (rows.isEmpty()) {
            log.info("급상승 집계 - 구간 {} 적재 컷(raw_score >= {}) 통과 글 없음",
                    bucketStart, statsPolicy.trendingMinDeltaScore());
            return;
        }

        rankAndSelect(rows);
        upsertInChunks(rows);

        long trendingCount = rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsTrending())).count();
        log.info("급상승 집계 완료 - 구간 {} 후보 {}건 → 적재 {}건, 급상승 {}건 (접속자 보정 분모 {})",
                bucketStart, candidates.size(), rows.size(), trendingCount, denominator);
    }

    private Map<Long, PostStatHistory> loadHistories(List<TrendingCandidate> candidates,
                                                     LocalDateTime bucketStart,
                                                     int windows) {
        List<Long> postIds = candidates.stream().map(TrendingCandidate::getPostId).toList();
        Map<Long, PostStatHistory> histories = new HashMap<>();
        for (PostStatHistory history : statsPostMapper.findHistories(postIds, bucketStart, windows)) {
            histories.put(history.getPostId(), history);
        }
        return histories;
    }

    /**
     * 구간 접속자 수 — 점수의 분모. 컬럼으로 저장하지 않고 필요할 때 집계한다.
     *
     * access_hour 는 시간 단위로 절삭돼 있으므로, 집계 주기가 1시간보다 짧으면 구간 하한을 그 구간이 속한
     * 정시로 내려 세야 분모가 0으로 무너지지 않는다.
     */
    private int countActiveUsers(LocalDateTime bucketStart, LocalDateTime bucketEnd) {
        return statsAccessMapper.countActiveUsers(bucketStart.truncatedTo(ChronoUnit.HOURS), bucketEnd);
    }

    private List<PostStatRow> buildRows(List<TrendingCandidate> candidates,
                                        Map<Long, PostStatHistory> histories,
                                        LocalDateTime bucketStart,
                                        LocalDateTime bucketEnd,
                                        int windows,
                                        int denominator) {
        double viewWeight = statsPolicy.trendingWeightView();
        double likeWeight = statsPolicy.trendingWeightLike();
        double commentWeight = statsPolicy.trendingWeightComment();
        double minDeltaScore = statsPolicy.trendingMinDeltaScore();
        int freshnessScaleHours = statsPolicy.trendingFreshnessScaleHours();

        List<PostStatRow> rows = new ArrayList<>();
        for (TrendingCandidate candidate : candidates) {
            PostStatHistory history = histories.get(candidate.getPostId());

            // 첫 등장은 기준선 0 (COALESCE) → 증가분 = 현재 누적값
            int viewDelta = candidate.getViewCount() - (history != null ? history.getLastViewCount() : 0);
            int likeDelta = candidate.getLikeCount() - (history != null ? history.getLastLikeCount() : 0);
            int commentDelta = candidate.getCommentCount() - (history != null ? history.getLastCommentCount() : 0);

            double rawScore = viewDelta * viewWeight + likeDelta * likeWeight + commentDelta * commentWeight;
            if (rawScore < minDeltaScore) {
                continue; // 적재 컷 미달 — 행을 만들지 않는다
            }

            // 평소 수준 = 직전 N구간 raw_score 합 / N. 이력이 없으면 0 이고 spike_ratio 는 NULL(관문2 면제)
            boolean hasHistory = history != null && history.getWindowRowCount() > 0;
            double baselineScore = hasHistory ? history.getWindowScoreSum() / windows : 0.0;
            Double spikeRatio = baselineScore > 0 ? rawScore / baselineScore : null;

            double ageHours = Math.max(0, Duration.between(candidate.getCreatedAt(), bucketEnd).toMinutes()) / 60.0;
            double freshness = 1.0 / (1.0 + ageHours / freshnessScaleHours);

            PostStatRow row = new PostStatRow();
            row.setStatHour(bucketStart);
            row.setPostId(candidate.getPostId());
            row.setBoardId(candidate.getBoardId());
            row.setReadScope(candidate.getReadScope());
            row.setViewCount(candidate.getViewCount());
            row.setLikeCount(candidate.getLikeCount());
            row.setCommentCount(candidate.getCommentCount());
            row.setViewDelta(viewDelta);
            row.setLikeDelta(likeDelta);
            row.setCommentDelta(commentDelta);
            row.setRawScore(rawScore);
            row.setBaselineScore(baselineScore);
            row.setSpikeRatio(spikeRatio);
            row.setFreshness(freshness);
            row.setFinalScore(rawScore / denominator * freshness);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 순위 부여 + 급상승 3관문 판정 (셋 다 통과해야 선정).
     * <ol>
     *   <li>raw_score >= trending_min_score — 절대 활동량</li>
     *   <li>spike_ratio >= trending_spike_ratio — 평소 대비 배수. 이력 없는 신규 글은 NULL 이므로 면제</li>
     *   <li>rank_no <= trending_top_n — 구간 내 상위 N</li>
     * </ol>
     */
    private void rankAndSelect(List<PostStatRow> rows) {
        // 동점일 때 post_id 로 확정 정렬 → 재실행해도 순위가 같다
        rows.sort(Comparator.comparingDouble(PostStatRow::getFinalScore).reversed()
                .thenComparing(PostStatRow::getPostId));

        double minScore = statsPolicy.trendingMinScore();
        double spikeThreshold = statsPolicy.trendingSpikeRatio();
        int topN = statsPolicy.trendingTopN();

        int rank = 1;
        for (PostStatRow row : rows) {
            row.setRankNo(rank);
            boolean passesVolume = row.getRawScore() >= minScore;
            boolean passesSpike = row.getSpikeRatio() == null || row.getSpikeRatio() >= spikeThreshold;
            boolean passesRank = rank <= topN;
            row.setIsTrending(passesVolume && passesSpike && passesRank);
            rank++;
        }
    }

    private void upsertInChunks(List<PostStatRow> rows) {
        for (int from = 0; from < rows.size(); from += UPSERT_CHUNK_SIZE) {
            int to = Math.min(from + UPSERT_CHUNK_SIZE, rows.size());
            statsPostMapper.upsertPostStats(rows.subList(from, to));
        }
    }

    // 자정 기준으로 intervalMinutes 배수 경계에 맞춰 절삭 (interval=60이면 정시)
    private LocalDateTime truncateToInterval(LocalDateTime time, int intervalMinutes) {
        LocalDateTime dayStart = time.truncatedTo(ChronoUnit.DAYS);
        long minutesSinceDayStart = Duration.between(dayStart, time).toMinutes();
        return dayStart.plusMinutes(minutesSinceDayStart - (minutesSinceDayStart % intervalMinutes));
    }
}
