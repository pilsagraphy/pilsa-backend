package com.back.auth.local.service;

import com.back.auth.local.mapper.WithdrawMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 탈퇴 행 정리 배치 (일 1회 04:30 — 제재 캐시 정리 04:00 이후).
 *
 * 탈퇴/재가입 반복으로 쌓이는 익명화 행이 무기한 잔존하지 않도록,
 * **보존기간(기본 90일)이 지났고 활동·제재 이력이 전혀 없는** 탈퇴 행만 물리 삭제한다
 * — 네이버/카카오식 "파기 + 부정이용 방지 정보만 기간 보존 후 자동 삭제" 관행과 동일.
 *
 * 글/댓글/제재 이력이 있는 탈퇴 행은 삭제하지 않는다:
 *  - 작성자 표기('탈퇴한 회원')가 users 조인이라 행을 지우면 그 글이 목록에서 통째로 사라진다
 *  - 제재 이력 행(학번 해시)은 재가입 차단의 근거다
 * 보존기간은 쿨다운(rejoin_cooldown_days)보다 길어야 쿨다운 판정 근거가 유지된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnUserPurgeScheduler {

    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final WithdrawMapper withdrawMapper;

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgeInactiveWithdrawnUsers() {
        int retentionDays = parseDays(withdrawMapper.findPolicySetting("withdrawn_purge_days"));

        List<Long> targets = withdrawMapper.findPurgeableWithdrawnUserIds(retentionDays);
        if (targets.isEmpty()) {
            return;
        }
        // 알림 행이 남아 있으면 FK 가 users 삭제를 막으므로 먼저 정리 (알림은 증적 가치 없음)
        withdrawMapper.deleteNotificationsByUserIds(targets);
        int purged = withdrawMapper.deleteUsersByIds(targets);
        log.info("탈퇴 행 정리 배치 - 보존 {}일 경과·이력 없음 {}건 물리 삭제", retentionDays, purged);
    }

    private int parseDays(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return DEFAULT_RETENTION_DAYS;
        }
    }
}
