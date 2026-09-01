package com.back.stats.policy;

import com.back.stats.policy.mapper.StatsPolicyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 정책 로더가 <b>어떤 경우에도 예외를 던지지 않는지</b>, 그리고 집계 주기 제약을 지키는지 검증한다.
 *
 * 둘 다 조용히 망가지는 종류라 테스트로 고정한다:
 *  - 조회 실패가 밖으로 나가면 급상승 트리거가 다음 실행 시각을 못 구해 스케줄이 영구히 멈춘다
 *  - 60의 배수가 아닌 주기는 접속자 분모(시간 버킷)를 틀어지게 만드는데, 에러 없이 점수만 이상해진다
 */
class StatsPolicyTest {

    private StatsPolicyMapper statsPolicyMapper;
    private StatsPolicy statsPolicy;

    @BeforeEach
    void setUp() {
        statsPolicyMapper = mock(StatsPolicyMapper.class);
        statsPolicy = new StatsPolicy(statsPolicyMapper);
    }

    @Test
    @DisplayName("DB 조회가 실패해도 예외를 던지지 않고 기본값을 준다 (스케줄이 죽지 않게)")
    void fallsBackToDefaultWhenLookupFails() {
        when(statsPolicyMapper.findPolicySetting(anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThat(statsPolicy.trendingIntervalMinutes()).isEqualTo(60);
        assertThat(statsPolicy.trendingMinScore()).isEqualTo(10.0);
        assertThat(statsPolicy.statsRetentionDays()).isEqualTo(1825);
    }

    @Test
    @DisplayName("숫자로 읽을 수 없는 값·빈 값·행 없음은 모두 기본값")
    void fallsBackToDefaultOnUnusableValue() {
        when(statsPolicyMapper.findPolicySetting(StatsPolicy.TRENDING_TOP_N)).thenReturn("다섯");
        when(statsPolicyMapper.findPolicySetting(StatsPolicy.TRENDING_MIN_ACTIVE_USERS)).thenReturn("  ");
        when(statsPolicyMapper.findPolicySetting(StatsPolicy.TRENDING_HALFLIFE_HOURS)).thenReturn(null);

        assertThat(statsPolicy.trendingTopN()).isEqualTo(5);
        assertThat(statsPolicy.trendingMinActiveUsers()).isEqualTo(5);
        assertThat(statsPolicy.trendingHalflifeHours()).isEqualTo(24);
    }

    @Test
    @DisplayName("집계 주기는 60의 배수만 통과하고, 아니면 60으로 되돌린다")
    void acceptsOnlyHourAlignedInterval() {
        when(statsPolicyMapper.findPolicySetting(StatsPolicy.TRENDING_INTERVAL_MINUTES)).thenReturn("120");
        assertThat(statsPolicy.trendingIntervalMinutes()).isEqualTo(120);

        when(statsPolicyMapper.findPolicySetting(StatsPolicy.TRENDING_INTERVAL_MINUTES)).thenReturn("30");
        assertThat(statsPolicy.trendingIntervalMinutes()).isEqualTo(60);

        when(statsPolicyMapper.findPolicySetting(StatsPolicy.TRENDING_INTERVAL_MINUTES)).thenReturn("0");
        assertThat(statsPolicy.trendingIntervalMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("정상 값은 그대로 읽는다 (DB 한 줄로 조정된다는 전제)")
    void readsValidValue() {
        when(statsPolicyMapper.findPolicySetting(StatsPolicy.TRENDING_SPIKE_RATIO)).thenReturn("2.5");
        when(statsPolicyMapper.findPolicySetting(StatsPolicy.TRENDING_MIN_ACTIVE_USERS)).thenReturn("12");

        assertThat(statsPolicy.trendingSpikeRatio()).isEqualTo(2.5);
        assertThat(statsPolicy.trendingMinActiveUsers()).isEqualTo(12);
    }
}
