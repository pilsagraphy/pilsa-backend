package com.back.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync // 메인 클래스 대신 여기서 비동기 기능을 켬.
public class AsyncConfig {

    /**
     * 사람이 기다리는 알림 전용 실행기 — 웹 푸시와 메일(인증번호).
     *
     * 예전에는 이것들이 구글 캘린더 동기화와 {@code taskExecutor} 하나를 같이 썼다(스레드 2개).
     * 캘린더 연동자가 늘어 동기화가 길어지면 그 뒤에 푸시가 줄을 서게 되고, 그러면 댓글 알림이 몇 분씩 늦는다.
     * 성격이 다른 일이라 풀을 나눈다 — 캘린더는 좀 늦어도 되지만 알림은 늦으면 의미가 없다.
     *
     * <p><b>재배포 때 잃지 않도록</b> 종료 시 진행 중인 작업을 기다린다. @Async 작업은 JVM 이 내려가면
     * 큐에 남아 있든 실행 중이든 그대로 사라지는데, 그러면 알림 행은 저장됐는데 푸시만 안 나간 상태가 되고
     * 아무 로그도 남지 않는다. 20초는 기다려 준다(푸시 한 건은 보통 1초 안에 끝난다).
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        // 큐까지 가득 차면 호출한 스레드가 직접 보낸다. 댓글 등록이 그만큼 느려지지만 알림을 잃지는 않는다
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
