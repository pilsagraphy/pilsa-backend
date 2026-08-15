package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationDeviceRequest;
import com.back.mypage.notification.dto.NotificationPageResponse;
import com.back.mypage.notification.dto.NotificationResponse;
import com.back.mypage.notification.dto.NotificationType;
import com.back.mypage.notification.exception.NotificationException;
import com.back.mypage.notification.mapper.NotificationDeviceMapper;
import com.back.mypage.notification.mapper.NotificationMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 발행/조회 서비스.
 *
 * 발행(notifyXxx)은 본 기능(댓글 등록, 신고 처리 등)의 부가 작업이므로 REQUIRES_NEW 로 분리한다.
 * 알림 저장이 실패해도 본 기능 트랜잭션은 롤백되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationDeviceMapper notificationDeviceMapper;
    private final NotificationPushService notificationPushService;

    // ---- 조회 ----

    public NotificationPageResponse getMyNotifications(int page, int size, boolean unreadOnly) {
        Long userId = AuthUtils.currentUserId();
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), 100);

        int totalCount = notificationMapper.countByUser(userId, unreadOnly);
        List<NotificationResponse> list = totalCount == 0
                ? List.of()
                : notificationMapper.findByUser(userId, unreadOnly, (page - 1) * size, size);

        NotificationPageResponse response = new NotificationPageResponse();
        response.setTotalCount(totalCount);
        response.setTotalPages((int) Math.ceil((double) totalCount / size));
        response.setUnreadCount(notificationMapper.countUnread(userId));
        response.setNotifications(list);
        return response;
    }

    public int getUnreadCount() {
        return notificationMapper.countUnread(AuthUtils.currentUserId());
    }

    // ---- 읽음/삭제 ----

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationMapper.markAsRead(notificationId, AuthUtils.currentUserId());
    }

    @Transactional
    public int markAllAsRead() {
        return notificationMapper.markAllAsRead(AuthUtils.currentUserId());
    }

    @Transactional
    public void delete(Long notificationId) {
        notificationMapper.deleteNotification(notificationId, AuthUtils.currentUserId());
    }

    // ---- 발행 ----

    // 내 글에 댓글이 달림
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyComment(Long receiverId, Long actorId, Long postId, String linkUrl) {
        publish(receiverId, actorId, NotificationType.COMMENT, null, linkUrl, "post", postId);
    }

    // 내 댓글에 답글이 달림
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyReply(Long receiverId, Long actorId, Long postId, String linkUrl) {
        publish(receiverId, actorId, NotificationType.REPLY, null, linkUrl, "post", postId);
    }

    // 내가 신고한 건이 처리됨
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyReportResolved(Long receiverId, String message, String targetType, Long targetId) {
        publish(receiverId, null, NotificationType.REPORT_RESOLVED, message, null, targetType, targetId);
    }

    // 제재(주의/경고/정지/차단) 적용됨
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifySanction(Long receiverId, String message) {
        publish(receiverId, null, NotificationType.SANCTION, message, null, "user", receiverId);
    }

    private void publish(Long receiverId, Long actorId, NotificationType type,
                         String message, String linkUrl, String targetType, Long targetId) {
        // 수신자가 없거나 본인 행동으로 인한 알림은 발행하지 않는다
        if (receiverId == null || receiverId.equals(actorId)) {
            return;
        }
        try {
            notificationMapper.insertNotification(receiverId, type.name(), type.defaultTitle(),
                    message, linkUrl, targetType, targetId);
            // 등록된 기기(웹앱·모바일 브라우저)로도 발송 — 비동기라 본 기능을 지연시키지 않는다
            notificationPushService.sendToUser(receiverId, type.defaultTitle(), message, linkUrl);
        } catch (Exception e) {
            log.warn("알림 발행 실패 - userId: {}, type: {}, {}", receiverId, type, e.getMessage());
        }
    }

    // ---- 알림 수신 기기 등록부 (웹 푸시 전달 채널. 캘린더 구독과 무관) ----

    @Transactional
    public void registerDevice(NotificationDeviceRequest request) {
        if (request.getEndpoint() == null || request.getEndpoint().isBlank()
                || request.getKeys() == null
                || request.getKeys().getP256dh() == null || request.getKeys().getP256dh().isBlank()
                || request.getKeys().getAuth() == null || request.getKeys().getAuth().isBlank()) {
            throw new NotificationException("기기 등록 정보가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        notificationDeviceMapper.upsertDevice(AuthUtils.currentUserId(),
                request.getEndpoint(), request.getKeys().getP256dh(), request.getKeys().getAuth());
    }

    @Transactional
    public void unregisterDevice(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new NotificationException("해제할 기기의 endpoint 가 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        // 이미 없는 기기여도 결과 상태는 동일하므로 성공으로 처리
        notificationDeviceMapper.deleteByEndpoint(AuthUtils.currentUserId(), endpoint);
    }
}
