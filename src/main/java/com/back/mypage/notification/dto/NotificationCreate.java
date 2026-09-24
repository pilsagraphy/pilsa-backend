package com.back.mypage.notification.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 알림 1건 발행용 입력 + 생성 키 회수 객체.
 *
 * insertNotification 이 useGeneratedKeys 로 {@link #notificationId} 를 채워 돌려준다
 * → 그 id 를 그대로 푸시 발송(sendToUser)의 toastId 로 넘긴다.
 */
@Getter
@Setter
public class NotificationCreate {

    private Long userId;         // 수신 회원
    private String type;         // NotificationType.name()
    private String title;        // 화면·푸시 제목
    private String message;      // 본문 (nullable — 현재는 title 만 사용)
    private String targetType;   // 이동 대상 유형: post / comment / user (nullable)
    private Long targetId;       // 이동 대상 PK (nullable)
    private Long notificationId; // INSERT 후 생성 키가 채워진다 (useGeneratedKeys)

    public NotificationCreate(Long userId, String type, String title, String message,
                              String targetType, Long targetId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.targetType = targetType;
        this.targetId = targetId;
    }
}
