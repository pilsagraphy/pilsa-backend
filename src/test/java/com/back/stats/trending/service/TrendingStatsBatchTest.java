package com.back.stats.trending.service;

import com.back.stats.access.mapper.StatsAccessMapper;
import com.back.stats.policy.StatsPolicy;
import com.back.stats.trending.dto.PostStatHistory;
import com.back.stats.trending.dto.PostStatRow;
import com.back.stats.trending.dto.TrendingCandidate;
import com.back.stats.trending.mapper.StatsPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 급상승 점수·판정 규칙 검증 (DB 없이 매퍼를 대역으로 둔다).
 *
 * 이 테스트가 지키는 계약:
 *  - 적재 컷 미달 구간은 행을 만들지 않는다
 *  - 이력 없는 글은 spike_ratio 가 NULL 이고 관문2를 면제받는다
 *  - baseline 은 행 평균이 아니라 직전 N구간 SUM/N 이다
 *  - 기준선은 구간 시작 "이전" 행만 본다 (재실행 안전의 근거)
 */
class TrendingStatsBatchTest {

    private static final LocalDateTime BUCKET_START = LocalDateTime.of(2026, 8, 18, 14, 0);
    private static final LocalDateTime BUCKET_END = LocalDateTime.of(2026, 8, 18, 15, 0);

    private StatsPostMapper statsPostMapper;
    private StatsAccessMapper statsAccessMapper;
    private StatsPolicy statsPolicy;
    private TrendingStatsBatch trendingStatsBatch;

    @BeforeEach
    void setUp() {
        statsPostMapper = mock(StatsPostMapper.class);
        statsAccessMapper = mock(StatsAccessMapper.class);
        statsPolicy = mock(StatsPolicy.class);
        trendingStatsBatch = new TrendingStatsBatch(statsPostMapper, statsAccessMapper, statsPolicy);

        when(statsPolicy.trendingPostMaxAgeHours()).thenReturn(168);
        when(statsPolicy.trendingBaselineWindows()).thenReturn(6);
        when(statsPolicy.trendingWeightView()).thenReturn(1.0);
        when(statsPolicy.trendingWeightLike()).thenReturn(5.0);
        when(statsPolicy.trendingWeightComment()).thenReturn(3.0);
        when(statsPolicy.trendingMinDeltaScore()).thenReturn(5.0);
        when(statsPolicy.trendingMinScore()).thenReturn(10.0);
        when(statsPolicy.trendingSpikeRatio()).thenReturn(3.0);
        when(statsPolicy.trendingTopN()).thenReturn(5);
        when(statsPolicy.trendingActiveUserFloor()).thenReturn(5);
        when(statsPolicy.trendingFreshnessScaleHours()).thenReturn(24);
        // 구간 접속자 10명 → 분모는 max(10, 하한 5) = 10
        when(statsAccessMapper.countActiveUsers(any(), any())).thenReturn(10);
    }

    @Test
    @DisplayName("적재 컷 미달 글은 행을 만들지 않고, 첫 등장 글은 spike_ratio NULL 로 관문2를 면제받는다")
    void skipsBelowCutAndExemptsFirstAppearance() {
        // 첫 등장(이력 없음): 증가분 = 누적값 20/2/1 → raw = 20 + 10 + 3 = 33
        TrendingCandidate fresh = candidate(1L, BUCKET_END, 20, 2, 1);
        // 이력 있으나 증가분이 조회 2뿐 → raw = 2 (컷 5 미달)
        TrendingCandidate quiet = candidate(2L, BUCKET_END.minusHours(24), 102, 5, 5);

        when(statsPostMapper.findCandidates(eq(BUCKET_END), anyInt())).thenReturn(List.of(fresh, quiet));
        when(statsPostMapper.findHistories(anyList(), eq(BUCKET_START), anyInt()))
                .thenReturn(List.of(history(2L, 100, 5, 5, 6, 600)));

        trendingStatsBatch.aggregateBucket(BUCKET_START, BUCKET_END);

        Map<Long, PostStatRow> stored = capturedRows();
        assertThat(stored.keySet()).containsExactly(1L);

        PostStatRow row = stored.get(1L);
        assertThat(row.getStatHour()).isEqualTo(BUCKET_START);
        assertThat(row.getViewDelta()).isEqualTo(20);
        assertThat(row.getRawScore()).isEqualTo(33.0);
        assertThat(row.getSpikeRatio()).isNull();          // 이력 없음 → 관문2 면제
        assertThat(row.getBaselineScore()).isZero();
        assertThat(row.getFreshness()).isEqualTo(1.0);      // 방금 작성 → 감쇠 없음
        assertThat(row.getFinalScore()).isEqualTo(3.3);     // 33 / 10 × 1.0
        assertThat(row.getRankNo()).isEqualTo(1);
        assertThat(row.getIsTrending()).isTrue();
    }

    @Test
    @DisplayName("baseline 은 직전 N구간 SUM/N 이고, 평소 대비 배수가 기준 미달이면 급상승이 아니다")
    void computesBaselineAsSumOverWindows() {
        // 직전 6구간 raw_score 합 600 → baseline 100. 이번 증가분 조회 20 → raw 20, 배수 0.2 (< 3.0)
        TrendingCandidate steady = candidate(7L, BUCKET_END.minusHours(24), 120, 5, 5);

        when(statsPostMapper.findCandidates(eq(BUCKET_END), anyInt())).thenReturn(List.of(steady));
        when(statsPostMapper.findHistories(anyList(), eq(BUCKET_START), anyInt()))
                .thenReturn(List.of(history(7L, 100, 5, 5, 6, 600)));

        trendingStatsBatch.aggregateBucket(BUCKET_START, BUCKET_END);

        PostStatRow row = capturedRows().get(7L);
        assertThat(row.getRawScore()).isEqualTo(20.0);
        assertThat(row.getBaselineScore()).isEqualTo(100.0);   // 600 / 6 — 행 평균이 아니다
        assertThat(row.getSpikeRatio()).isEqualTo(0.2);
        assertThat(row.getFreshness()).isEqualTo(0.5);         // 24시간 경과 → 절반
        assertThat(row.getIsTrending()).isFalse();             // 관문1 통과, 관문2 탈락

        // 기준선은 이번 구간 "이전" 행만 본다 — 같은 구간 재실행 시 자기 행이 기준선이 되지 않는 근거
        verify(statsPostMapper).findHistories(anyList(), eq(BUCKET_START), eq(6));
    }

    @Test
    @DisplayName("순위는 final_score 내림차순이고, top_n 을 넘은 글은 다른 관문을 통과해도 급상승이 아니다")
    void limitsTrendingToTopN() {
        when(statsPolicy.trendingTopN()).thenReturn(1);

        TrendingCandidate hot = candidate(11L, BUCKET_END, 50, 0, 0);      // raw 50, freshness 1.0 → final 5.0
        TrendingCandidate warm = candidate(12L, BUCKET_END, 30, 0, 0);     // raw 30, freshness 1.0 → final 3.0

        when(statsPostMapper.findCandidates(eq(BUCKET_END), anyInt())).thenReturn(List.of(warm, hot));
        when(statsPostMapper.findHistories(anyList(), eq(BUCKET_START), anyInt())).thenReturn(List.of());

        trendingStatsBatch.aggregateBucket(BUCKET_START, BUCKET_END);

        Map<Long, PostStatRow> stored = capturedRows();
        assertThat(stored.get(11L).getRankNo()).isEqualTo(1);
        assertThat(stored.get(11L).getIsTrending()).isTrue();
        assertThat(stored.get(12L).getRankNo()).isEqualTo(2);
        assertThat(stored.get(12L).getIsTrending()).isFalse();  // 관문3 탈락
    }

    @Test
    @DisplayName("후보가 없으면 적재를 시도하지 않는다")
    void skipsUpsertWhenNoCandidate() {
        when(statsPostMapper.findCandidates(eq(BUCKET_END), anyInt())).thenReturn(List.of());

        trendingStatsBatch.aggregateBucket(BUCKET_START, BUCKET_END);

        verify(statsPostMapper, org.mockito.Mockito.never()).upsertPostStats(anyList());
    }

    @SuppressWarnings("unchecked")
    private Map<Long, PostStatRow> capturedRows() {
        ArgumentCaptor<List<PostStatRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(statsPostMapper).upsertPostStats(captor.capture());
        return captor.getValue().stream()
                .collect(Collectors.toMap(PostStatRow::getPostId, Function.identity(),
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private TrendingCandidate candidate(Long postId, LocalDateTime createdAt,
                                        int viewCount, int likeCount, int commentCount) {
        TrendingCandidate candidate = new TrendingCandidate();
        candidate.setPostId(postId);
        candidate.setBoardId(2L);
        candidate.setReadScope("MEMBER");
        candidate.setCreatedAt(createdAt);
        candidate.setViewCount(viewCount);
        candidate.setLikeCount(likeCount);
        candidate.setCommentCount(commentCount);
        return candidate;
    }

    private PostStatHistory history(Long postId, int lastView, int lastLike, int lastComment,
                                    int windowRowCount, double windowScoreSum) {
        PostStatHistory history = new PostStatHistory();
        history.setPostId(postId);
        history.setLastViewCount(lastView);
        history.setLastLikeCount(lastLike);
        history.setLastCommentCount(lastComment);
        history.setWindowRowCount(windowRowCount);
        history.setWindowScoreSum(windowScoreSum);
        return history;
    }
}
