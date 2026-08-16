package com.back.mypage.notification.mapper;

import com.back.mypage.notification.dto.NotificationResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    // 내 알림 목록 (unreadOnly=true 면 안 읽은 것만)
    List<NotificationResponse> findByUser(
            @Param("userId") Long userId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("offset") int offset,
            @Param("size") int size
    );

    int countByUser(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly);

    // 종 아이콘 뱃지용 미읽음 개수
    int countUnread(@Param("userId") Long userId);

    // 단건 읽음 처리 (본인 것만)
    int markAsRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    // 전체 읽음 처리
    int markAllAsRead(@Param("userId") Long userId);

    // 알림 삭제 (소프트)
    int deleteNotification(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    /** 탈퇴 시 본인 알림 일괄 정리 (수신자 본인만 보는 데이터라 증적 가치 없음) */
    int softDeleteAllByUser(@Param("userId") Long userId);
}
