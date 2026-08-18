package com.back.stats.trending.service;

import com.back.stats.policy.StatsPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.time.Duration;

/**
 * 급상승 집계 배치의 실행 주기 등록.
 *
 * {@code @Scheduled(fixedRate=...)} 를 쓰지 않는 이유는 주기 자체가 정책 수치(policy_settings.trending_interval_minutes)라서다 —
 * 애노테이션 상수로 굳히면 주기를 바꿀 때 재배포가 필요하다. 트리거를 실행마다 다시 만들어 다음 발사 시각을
 * 계산하므로, DB 값을 고치면 <b>다음 실행부터</b> 새 주기가 적용된다.
 *
 * 첫 실행은 기동 직후를 피해 한 주기 뒤로 미룬다(기동 중 커넥션풀·캐시 워밍업과 겹치지 않게).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TrendingScheduleConfig implements SchedulingConfigurer {

    private final TrendingStatsBatch trendingStatsBatch;
    private final StatsPolicy statsPolicy;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::runSafely, context -> {
            Duration interval = Duration.ofMinutes(statsPolicy.trendingIntervalMinutes());
            PeriodicTrigger trigger = new PeriodicTrigger(interval);
            trigger.setInitialDelay(interval);
            return trigger.nextExecution(context);
        });
    }

    // 한 번의 실패로 트리거가 죽어 이후 구간이 전부 비지 않게 예외를 흡수한다.
    // 건너뛴 구간의 증가분은 다음 성공 실행의 누적값 차이로 회수된다(손실 없음).
    private void runSafely() {
        try {
            trendingStatsBatch.aggregateLatestBucket();
        } catch (Exception e) {
            log.error("급상승 집계 배치 실패 - 다음 주기에 재시도", e);
        }
    }
}
