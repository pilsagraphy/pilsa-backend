package com.back.mypage.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 알림 목록 페이지 응답 (unreadCount 는 종 아이콘 뱃지용)
@Getter
@Setter
public class NotificationPageResponse {
    private int totalPages;
    private int totalCount;
    private int unreadCount;
    private List<NotificationResponse> notifications;
}
