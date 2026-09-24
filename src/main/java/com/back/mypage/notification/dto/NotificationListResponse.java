package com.back.mypage.notification.dto;

import lombok.Getter;

import java.util.List;

/**
 * 알림함 목록 응답. 페이징 없이 최근 N개월치를 전체 반환한다
 * (N = policy_settings.notification_list_months).
 */
@Getter
public class NotificationListResponse {
    private final int unreadCount;                 // 미읽음 개수 (뱃지와 동일 정의 — N개월 창 기준)
    private final List<NotificationResponse> toasts;

    public NotificationListResponse(int unreadCount, List<NotificationResponse> toasts) {
        this.unreadCount = unreadCount;
        this.toasts = toasts;
    }
}
