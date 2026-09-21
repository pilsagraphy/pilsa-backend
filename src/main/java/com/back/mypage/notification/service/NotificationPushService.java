package com.back.mypage.notification.service;

import com.back.mypage.notification.dto.NotificationDevice;
import com.back.mypage.notification.mapper.NotificationDeviceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
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
    // 캘린더 동기화와 풀을 나눈다 — 동기화가 길어질 때 알림이 그 뒤에 줄 서지 않게 (AsyncConfig 참고)
    @Async("notificationExecutor")
    public void sendToUser(Long receiverId, Long toastId, String title, String body,
                           String targetType, Long targetId, Long boardId) {
        sendToUser(receiverId, toastId, null, title, body, targetType, targetId, boardId);
    }

    /** type(NotificationType 이름)까지 실어 보낸다 — 프론트가 중요 글·일정 알림을 댓글과 다르게 그린다 */
    @Async("notificationExecutor")
    public void sendToUser(Long receiverId, Long toastId, String type, String title, String body,
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
            json.put("type", type);
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
                // 콘텐츠 암호화는 RFC 8291 aes128gcm 으로 명시한다. 이 라이브러리의 send(notification) 기본값은 구형 aesgcm
                // (Crypto-Key 헤더)이라 Apple 푸시(web.push.apple.com — iPhone 홈 화면 앱)가 거절한다. 크롬/FCM 도 aes128gcm 이 표준.
                //
                // urgency HIGH — 푸시 서비스에 "모아 뒀다 나중에 주지 말고 지금 배달하라" 는 뜻이다.
                // 화면을 켜거나 잠금을 푸는 것과는 무관하고, 알림 표시 방식도 기기 설정을 그대로 따른다.
                // 기본값 normal 은 배터리를 아끼려 여러 건을 묶어 뒀다 나중에 주는 등급이라 댓글 알림에는 맞지 않는다.
                Notification push = Notification.builder()
                        .endpoint(device.getEndpoint())
                        .userPublicKey(device.getP256dh())
                        .userAuth(device.getAuthSecret())
                        .payload(payload)
                        // TTL 은 지정하지 않는다(라이브러리 기본값). 늦더라도 알림은 결국 도착해야 한다는 것이 PM 판단 —
                        // 4시간 넘으면 버리도록 했다가, 오는 게 안 오는 것보다 낫다고 정리했다.
                        .urgency(Urgency.HIGH)
                        .build();
                HttpResponse response = pushService.send(push, Encoding.AES128GCM);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    deviceMapper.deleteById(device.getDeviceId());
                    log.info("만료된 알림 기기 정리 - deviceId: {}", device.getDeviceId());
                } else if (status >= 400) {
                    log.warn("푸시 발송 실패 - deviceId: {}, status: {}", device.getDeviceId(), status);
                } else {
                    // 성공도 남긴다. 예전에는 실패만 찍어서 "몇 시에 보냈는지" 조차 사후에 확인할 수 없었고,
                    // 알림이 늦게 왔다는 제보가 들어와도 발송이 늦은 것인지 배달이 늦은 것인지 가릴 수 없었다
                    log.info("푸시 발송 - toastId: {}, userId: {}, deviceId: {}, status: {}",
                            toastId, receiverId, device.getDeviceId(), status);
                }
            } catch (Exception e) {
                log.warn("푸시 발송 오류 - deviceId: {}, {}", device.getDeviceId(), e.getMessage());
            }
        }
    }
}
