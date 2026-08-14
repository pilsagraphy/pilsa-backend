package com.back.mypage.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 알림 목록 한 행 (시안: 헤더 종 아이콘)
@Getter
@Setter
public class NotificationResponse {
    private Long notificationId;
    private String type;        // COMMENT / REPLY / REPORT_RESOLVED / SANCTION / NOTICE
    private String title;
    private String message;
    private String linkUrl;     // 클릭 시 이동 경로
    private String targetType;  // post / comment / user (nullable)
    private Long targetId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
