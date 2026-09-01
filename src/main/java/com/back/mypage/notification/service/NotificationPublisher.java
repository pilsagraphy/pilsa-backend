package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationCreate;
import com.back.mypage.notification.dto.NotificationType;
import com.back.mypage.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 알림 발행 단일 진입점 — 알림 행 저장 + 등록 기기 푸시.
 *
 * "어떤 사건에 누구에게 알림을 줄지"(수신자 판정·본인 제외·중복 제거)는 <b>호출 도메인의 정책</b>이다.
 * 이 클래스는 "1명에게 알림 1건"을 저장·발송하는 일반 기능만 갖는다 — 나중에 신고/제재 알림이 붙어도 재사용된다.
 *
 * <p><b>트랜잭션·실패 격리</b>: 일부러 {@code @Transactional} 을 붙이지 않는다.
 * 호출부(예: 댓글 등록)의 트랜잭션에 참여해 함께 커밋되되, 발행이 실패하면 <b>호출부의 try/catch 가 예외를 삼킨다</b>.
 * 만약 여기에 별도 트랜잭션 경계를 두면, 발행 예외가 그 경계에서 rollback-only 로 표시되어
 * 정작 본 기능(댓글 등록)의 커밋까지 되돌릴 수 있다. 그래서 경계를 두지 않는다.
 * 푸시({@link NotificationPushService#sendToUser})는 @Async + 자체 예외 처리라 외부 HTTP 지연·실패는 여기까지 오지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationMapper notificationMapper;
    private final NotificationPushService pushService;

    /**
     * 알림 1건 발행.
     *
     * @param receiverId 수신 회원
     * @param type       알림 유형 (제목은 {@link NotificationType#defaultTitle()})
     * @param targetType 이동 대상 유형 (post / comment / user)
     * @param targetId   이동 대상 PK
     * @param boardId    이동 대상이 속한 게시판 — 푸시 페이로드용(프론트가 화면 경로 조립).
     *                   notifications 에는 저장하지 않고, 목록/읽음 응답의 boardId 는 조회 시 posts 조인으로 유도한다.
     */
    public void publish(Long receiverId, NotificationType type,
                        String targetType, Long targetId, Long boardId) {
        String title = type.defaultTitle();
        String message = null; // 현재 목록·푸시는 title 만 사용. 본문이 필요해지면 여기서 채운다.

        NotificationCreate command = new NotificationCreate(
                receiverId, type.name(), title, message, targetType, targetId);
        notificationMapper.insertNotification(command);

        // 저장된 알림을 등록 기기로 발송(앱이 꺼져 있어도 OS 푸시). @Async — 실패해도 로그만 남는다.
        pushService.sendToUser(receiverId, command.getNotificationId(),
                title, message, targetType, targetId, boardId);
    }
}
