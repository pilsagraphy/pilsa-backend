package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationDeviceRequest;
import com.back.mypage.notification.dto.NotificationListResponse;
import com.back.mypage.notification.dto.NotificationReadResponse;
import com.back.mypage.notification.exception.NotificationException;
import com.back.mypage.notification.mapper.NotificationDeviceMapper;
import com.back.mypage.notification.mapper.NotificationMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 알림함 API(목록·뱃지·읽음·삭제) + 알림 수신 기기 등록부(웹 푸시 전달 채널).
 *
 * 알림 발행(어떤 사건에 누구에게 알림을 만들지)은 {@link NotificationPublisher} 가,
 * 실제 푸시 발송은 {@link NotificationPushService} 가 담당한다.
 *
 * 소유권: 모든 알림함 쿼리는 AuthUtils.currentUserId() 를 조건으로 걸어 남의 알림 접근을 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    // policy_settings.notification_list_months 행이 없을 때만 쓰는 방어값 (정상 경로는 항상 DB 값 사용)
    private static final int DEFAULT_LIST_MONTHS = 2;

    private final NotificationDeviceMapper notificationDeviceMapper;
    private final NotificationMapper notificationMapper;

    // ===== 알림함 =====

    /** 목록 — 페이징 없이 최근 N개월치 전체 + 미읽음 개수 */
    public NotificationListResponse getList() {
        Long me = AuthUtils.currentUserId();
        int months = listMonths();
        return new NotificationListResponse(
                notificationMapper.countUnread(me, months),
                notificationMapper.findByUser(me, months));
    }

    /** 뱃지 — 미읽음 개수 */
    public int getUnreadCount() {
        return notificationMapper.countUnread(AuthUtils.currentUserId(), listMonths());
    }

    /**
     * 단건 읽음. 미읽음이면 읽음 처리하고, 이미 읽었어도 200 + 이동정보 반환(멱등).
     * 없거나 본인 알림이 아니면(=SELECT 결과 없음) 404.
     */
    @Transactional
    public NotificationReadResponse markAsRead(Long toastId) {
        Long me = AuthUtils.currentUserId();
        notificationMapper.markAsRead(toastId, me); // 0행이어도 아래 SELECT 로 존재/소유 판정 (멱등)
        NotificationReadResponse res = notificationMapper.findMoveInfo(toastId, me);
        if (res == null) {
            throw new NotificationException("알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        res.setMessage("읽음 처리되었습니다.");
        res.setUnreadCount(notificationMapper.countUnread(me, listMonths()));
        return res;
    }

    /** 전체 읽음 */
    @Transactional
    public Map<String, Object> markAllAsRead() {
        Long me = AuthUtils.currentUserId();
        int months = listMonths();
        int updated = notificationMapper.markAllAsRead(me, months);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "전체 읽음 처리되었습니다.");
        body.put("updatedCount", updated);
        body.put("unreadCount", notificationMapper.countUnread(me, months)); // 처리 후이므로 0
        return body;
    }

    /** 단건 삭제(소프트). 없거나 본인 알림이 아니면 404 */
    @Transactional
    public Map<String, Object> delete(Long toastId) {
        Long me = AuthUtils.currentUserId();
        if (notificationMapper.softDelete(toastId, me) == 0) {
            throw new NotificationException("알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "삭제되었습니다.");
        body.put("toastId", toastId);
        body.put("unreadCount", notificationMapper.countUnread(me, listMonths()));
        return body;
    }

    private int listMonths() {
        Integer months = notificationMapper.findListMonths();
        return (months != null && months > 0) ? months : DEFAULT_LIST_MONTHS;
    }

    // ===== 수신 기기 등록부 =====

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
