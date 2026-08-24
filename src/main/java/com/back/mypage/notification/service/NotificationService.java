package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationDeviceRequest;
import com.back.mypage.notification.dto.NotificationDeviceStatusResponse;
import com.back.mypage.notification.dto.NotificationDeviceSummary;
import com.back.mypage.notification.dto.NotificationDeviceToggleResponse;
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
import java.util.List;
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

    /**
     * 알림 수신 동의 상태 조회.
     * "이 기기가 켜져 있나"는 서버가 판정할 수 없어(요청만으로 기기를 특정할 수 없다)
     * 내 기기 목록을 주고 프론트가 자기 endpoint 와 대조한다 — 자세한 이유는 응답 DTO 주석 참고.
     */
    public NotificationDeviceStatusResponse getDeviceStatus() {
        List<NotificationDeviceSummary> devices =
                notificationDeviceMapper.findSummaryByUserId(AuthUtils.currentUserId());
        return new NotificationDeviceStatusResponse(devices);
    }

    /**
     * 알림 수신 동의/거부 (등록·해제 통합).
     *
     * 프론트가 현재 서버 상태를 몰라도 원하는 상태만 보내면 되게 하나로 합쳤다.
     * 같은 요청을 두 번 보내도 결과가 같다 — 동의는 UPSERT, 거부는 없는 기기여도 성공 처리.
     */
    @Transactional
    public NotificationDeviceToggleResponse setDeviceEnabled(NotificationDeviceRequest request) {
        Long userId = AuthUtils.currentUserId();

        // Boolean 래퍼라 미전달이 null 로 구분된다 — primitive 면 false 로 채워져 "거부"로 오해된다
        if (request.getEnabled() == null) {
            throw new NotificationException("enabled 값이 필요합니다. (true=수신 동의, false=수신 거부)",
                    HttpStatus.BAD_REQUEST);
        }
        if (request.getEndpoint() == null || request.getEndpoint().isBlank()) {
            throw new NotificationException("기기 endpoint 가 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        boolean enabled = request.getEnabled();
        if (enabled) {
            // 동의는 암호화 키가 없으면 발송이 불가능하므로 필수
            if (request.getKeys() == null
                    || request.getKeys().getP256dh() == null || request.getKeys().getP256dh().isBlank()
                    || request.getKeys().getAuth() == null || request.getKeys().getAuth().isBlank()) {
                throw new NotificationException("기기 등록 정보가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
            }
            notificationDeviceMapper.upsertDevice(userId,
                    request.getEndpoint(), request.getKeys().getP256dh(), request.getKeys().getAuth());
        } else {
            // 세션성 데이터라 소프트삭제 예외 — 행을 물리 삭제한다
            notificationDeviceMapper.deleteByEndpoint(userId, request.getEndpoint());
        }

        int deviceCount = notificationDeviceMapper.findSummaryByUserId(userId).size();
        String message = enabled
                ? "이 기기로 알림을 받습니다."
                : "이 기기에서는 알림을 받지 않습니다.";
        return new NotificationDeviceToggleResponse(enabled, deviceCount, message);
    }
}
