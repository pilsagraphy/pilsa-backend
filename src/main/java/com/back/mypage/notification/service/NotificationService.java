package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationDeviceRequest;
import com.back.mypage.notification.exception.NotificationException;
import com.back.mypage.notification.mapper.NotificationDeviceMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
