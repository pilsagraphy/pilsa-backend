package com.back.mypage.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 알림 매퍼.
 *
 * 알림 발행(INSERT)과 알림함 화면용 조회/읽음/삭제 쿼리는 아직 없다 — 담당자 과제.
 * 과제 설명: docs/integration-20260814/HANDOFF-notification-tasks.md
 * (notifications 테이블은 이미 있으므로 스키마는 DB에서 직접 확인할 것)
 */
@Mapper
public interface NotificationMapper {

    /** 탈퇴 시 본인 알림 일괄 정리 (수신자 본인만 보는 데이터라 증적 가치 없음) */
    int softDeleteAllByUser(@Param("userId") Long userId);

    /** policy_settings 값 조회 (notification_retention_days 등) */
    String findPolicySetting(@Param("code") String code);

    /** 보존기간이 지난 알림 물리 삭제 — 새벽 정리 배치 전용 */
    int deleteExpiredNotifications(@Param("retentionDays") int retentionDays);
}
