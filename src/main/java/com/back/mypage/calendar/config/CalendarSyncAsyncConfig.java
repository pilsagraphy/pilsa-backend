package com.back.mypage.calendar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 구글 캘린더 동기화용 비동기 실행기.
 *
 * 기본 SimpleAsyncTaskExecutor 를 그대로 쓰면 요청마다 스레드를 새로 만들어
 * 연동 사용자가 늘수록 스레드가 폭증한다. 풀을 명시해 상한을 둔다.
 *
 * 큐가 가득 차면 CallerRunsPolicy 로 호출 스레드가 직접 처리한다 —
 * 일정 동기화는 버려지면 사용자 캘린더가 조용히 어긋나므로, 느려질지언정 잃지는 않게 한다.
 */
@Configuration
@EnableAsync
public class CalendarSyncAsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("google-sync-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
