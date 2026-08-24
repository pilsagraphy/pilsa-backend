package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationDevice;
import com.back.mypage.notification.mapper.NotificationDeviceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 알림을 등록된 기기(notification_devices)로 발송한다 — 웹앱(TWA)/모바일 브라우저의 토스트 알림 채널.
 *
 * 인앱 알림(notifications 테이블 + /api/user/mypage/toast API)이 원본이고, 이 서비스는 전달 채널일 뿐이다.
 * 발송이 실패해도(권한 거부·기기 등록 만료) 인앱 알림은 정상 동작한다.
 *
 * 발송은 @Async — 외부 HTTP 호출이 댓글 작성 트랜잭션을 지연·실패시키면 안 된다. 실패는 로그만 남긴다.
 */
@Slf4j
@Service
public class NotificationPushService {

    private final NotificationDeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public NotificationPushService(NotificationDeviceMapper deviceMapper,
                                   ObjectMapper objectMapper,
                                   @Value("${push.vapid.public-key}") String publicKey,
                                   @Value("${push.vapid.private-key}") String privateKey,
                                   @Value("${push.vapid.subject}") String subject) throws GeneralSecurityException {
        this.deviceMapper = deviceMapper;
        this.objectMapper = objectMapper;
        // VAPID 서명·페이로드 암호화(RFC 8291)에 BouncyCastle 필요
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.pushService = new PushService(publicKey, privateKey, subject);
    }

    /**
     * 수신자의 모든 등록 기기로 발송.
     * 404/410 응답은 "기기가 수신을 끊었다"(알림 차단·앱 삭제·브라우저 초기화)는 뜻이므로 그 행을 즉시 정리한다
     * — 이것만 지켜도 죽은 기기가 등록부에 쌓이지 않는다.
     *
     * toastId 는 저장된 알림 행의 PK — 발행 측은 반드시 **알림 행을 먼저 INSERT 하고 생성된 id 를 확보한 뒤**
     * 이 메서드를 불러야 한다. id 가 있어야 OS 알림을 누른 프론트가 읽음 API(PATCH .../toast/{toastId}/read)를
     * 호출해 뱃지를 줄일 수 있다.
     *
     * 이동 정보는 targetType/targetId/boardId — 화면 경로(linkUrl)를 백엔드가 만들어 넣지 않는다.
     * 게시판은 데이터로 정의되므로 백엔드는 프론트 라우팅을 알 수 없다. 조립은 프론트 몫.
     */
    @Async
    public void sendToUser(Long receiverId, Long toastId, String title, String body,
                           String targetType, Long targetId, Long boardId) {
        List<NotificationDevice> devices = deviceMapper.findByUserId(receiverId);
        if (devices.isEmpty()) {
            return;
        }

        byte[] payload;
        try {
            // 프론트 Service Worker 의 push 핸들러가 그대로 showNotification / 화면 경로 조립에 쓰는 형태
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("toastId", toastId);
            json.put("title", title);
            json.put("body", body != null ? body : title);
            json.put("toastId", toastId);
            json.put("targetType", targetType);
            json.put("targetId", targetId);
            json.put("boardId", boardId);
            payload = objectMapper.writeValueAsString(json).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("푸시 페이로드 생성 실패 - userId: {}, {}", receiverId, e.getMessage());
            return;
        }

        for (NotificationDevice device : devices) {
            try {
                HttpResponse response = pushService.send(
                        new Notification(device.getEndpoint(), device.getP256dh(), device.getAuthSecret(), payload));
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    deviceMapper.deleteById(device.getDeviceId());
                    log.info("만료된 알림 기기 정리 - deviceId: {}", device.getDeviceId());
                } else if (status >= 400) {
                    log.warn("푸시 발송 실패 - deviceId: {}, status: {}", device.getDeviceId(), status);
                }
            } catch (Exception e) {
                log.warn("푸시 발송 오류 - deviceId: {}, {}", device.getDeviceId(), e.getMessage());
            }
        }
    }
}
