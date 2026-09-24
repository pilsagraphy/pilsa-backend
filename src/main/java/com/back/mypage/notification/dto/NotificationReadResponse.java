package com.back.mypage.notification.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 단건 읽음 응답 (프론트와의 확정 계약 — 필드 임의 변경 금지).
 *
 * 사용자는 알림을 읽기만 하지 않고 그 대상으로 이동하므로, 읽음 응답 하나로 이동까지 되게 한다
 * (목록을 다시 부르지 않아도 되고, 푸시를 눌러 목록을 거치지 않고 들어온 경우에도 동작).
 * 이미 읽은 알림을 다시 호출해도 200 + 동일 응답(멱등).
 *
 * type/targetType/targetId/boardId 는 매퍼 SELECT 로 채우고, message/unreadCount 는 서비스가 채운다.
 */
@Getter
@Setter
public class NotificationReadResponse {
    private String message;
    private Long toastId;
    private String type;
    private String targetType;
    private Long targetId;
    private Long boardId;
    private int unreadCount;
}
