package com.back.mypage.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 알림함 목록 한 행 (헤더 종 아이콘).
 *
 * boardId 는 notifications 에 저장되지 않고, 조회 시 target(post) → posts 조인으로 유도한다.
 * 프론트는 boardId/targetType/targetId 로 화면 경로를 조립한다(linkUrl 은 내려가지 않는다).
 */
@Getter
@Setter
public class NotificationResponse {
    private Long toastId;
    private String type;         // COMMENT / REPLY / ...
    private String title;
    private String message;      // nullable
    private String targetType;   // post / comment / user (nullable)
    private Long targetId;        // nullable
    private Long boardId;         // post 대상일 때 posts 조인으로 유도 (그 외 null)
    private Boolean isRead;       // Boolean — primitive면 JSON 키가 read 로 나가는 버그 (CLAUDE.md)
    private LocalDateTime createdAt;
}
