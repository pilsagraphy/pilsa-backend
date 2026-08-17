package com.back.mypage.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 알림 매퍼.
 *
 * 알림함 화면(목록·읽음·삭제)에 필요한 쿼리는 아직 없다 — 담당자 과제.
 * 과제 설명: docs/integration-20260814/HANDOFF-notification-tasks.md
 */
@Mapper
public interface NotificationMapper {

    // 알림 발행
    void insertNotification(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("message") String message,
            @Param("linkUrl") String linkUrl,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId
    );

    /** 탈퇴 시 본인 알림 일괄 정리 (수신자 본인만 보는 데이터라 증적 가치 없음) */
    int softDeleteAllByUser(@Param("userId") Long userId);
}
