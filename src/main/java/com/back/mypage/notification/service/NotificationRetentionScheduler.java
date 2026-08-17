package com.back.mypage.notification.service;

import com.back.mypage.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 보존기간 정리 배치 (일 1회 04:40 — 제재 캐시 04:00, 탈퇴 행 04:30 다음 순번).
 *
 * PM 확정(2026-08-17): 알림은 1년 보존 후 물리 삭제.
 * 알림은 수신자 본인만 보는 UI 편의 데이터라 증적 가치가 없다 — 소프트삭제 대전제의 예외
 * (notification_devices 를 세션성으로 예외 처리한 것과 같은 논리).
 * 정리하지 않으면 회원 수 × 댓글 수만큼 무한 증가한다.
 *
 * 읽음/삭제(state) 여부와 무관하게 발생 시각(created_at) 기준 — "1년 지난 알림은 사라진다" 하나로 단순하게.
 * 보존일수는 policy_settings.notification_retention_days (기본 365).
 * 알림함 표시 기간(notification_list_months=2)보다 훨씬 길어야 하며, 이 배치가 그 하한을 침범할 일은 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetentionScheduler {

    private static final int DEFAULT_RETENTION_DAYS = 365;

    private final NotificationMapper notificationMapper;

    @Scheduled(cron = "0 40 4 * * *")
    @Transactional
    public void purgeExpiredNotifications() {
        int retentionDays = parseDays(notificationMapper.findPolicySetting("notification_retention_days"));

        int purged = notificationMapper.deleteExpiredNotifications(retentionDays);
        if (purged > 0) {
            log.info("알림 보존기간 정리 배치 - {}일 경과 {}건 물리 삭제", retentionDays, purged);
        }
    }

    private int parseDays(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return DEFAULT_RETENTION_DAYS;
        }
    }
}
