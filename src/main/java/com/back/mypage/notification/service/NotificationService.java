package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationDeviceRequest;
import com.back.mypage.notification.dto.NotificationDeviceStatusResponse;
import com.back.mypage.notification.dto.NotificationDeviceSummary;
import com.back.mypage.notification.dto.NotificationDeviceToggleResponse;
import com.back.mypage.notification.exception.NotificationException;
import com.back.mypage.notification.mapper.NotificationDeviceMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 수신 기기 등록부 (웹 푸시 전달 채널. 캘린더 구독과 무관).
 *
 * 알림 자체의 **발행**(어떤 사건에 누구에게 알림을 만들지)과 **알림함 화면용 API**(목록·읽음·삭제)는
 * 아직 없다 — 담당자 과제.
 * 과제 설명: docs/integration-20260814/HANDOFF-notification-tasks.md
 *
 * 실제 발송 수단은 {@link NotificationPushService} 에 이미 준비되어 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationDeviceMapper notificationDeviceMapper;

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
