package com.back.mypage.calendar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 캘린더 동기화 재시도 배치 (10분 주기).
 *
 * 일정 팬아웃은 관리자 요청을 막지 않으려고 비동기로 돌기 때문에, 구글이 잠깐 흔들리거나
 * 액세스 토큰 갱신이 실패하면 그대로 실패로 끝난다. 그 상태를 방치하면 사용자 캘린더가
 * 조용히 어긋난 채 남는다 — 사용자는 연동을 켜 뒀으니 들어올 거라 믿고 있는데 일정이 없다.
 *
 * 일 1회가 아니라 10분 주기인 이유: 일정은 당일에 바뀌는 경우가 많아 하루 뒤 복구는 늦다.
 * 대신 한 번에 처리할 양을 잘라(BATCH_LIMIT) 배치가 길게 물리지 않게 한다.
 *
 * 실제 재시도 조건(백오프·상한)은 {@link GoogleCalendarSyncService#retryFailed} 와
 * CalendarSyncMapper.findRetryTargets 에 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarSyncRetryScheduler {

    /** 한 회차 처리량 상한 — 밀린 게 많아도 10분 안에 끝나게 자른다. */
    private static final int BATCH_LIMIT = 200;

    private final GoogleCalendarSyncService syncService;

    @Scheduled(cron = "0 */10 * * * *")
    public void retryFailedSyncs() {
        try {
            syncService.retryFailed(GoogleCalendarSyncService.MAX_RETRY, BATCH_LIMIT);
        } catch (Exception e) {
            // 배치가 죽으면 다음 주기까지 재시도가 멈춘다 — 여기서 삼키고 다음 회차에 다시 시도한다
            log.error("캘린더 동기화 재시도 배치 실패", e);
        }
    }
}
