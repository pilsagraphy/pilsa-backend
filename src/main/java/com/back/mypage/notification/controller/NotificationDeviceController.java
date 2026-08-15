package com.back.mypage.notification.controller;

import com.back.mypage.notification.dto.NotificationDeviceRequest;
import com.back.mypage.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 알림 수신 기기 등록/해제 (웹앱·모바일 브라우저의 토스트 알림용).
 *
 * "이 기기로도 알림을 보내 달라"는 등록부를 관리한다 — 일정관리의 캘린더 구독과는 무관하다.
 * 알림 자체는 기존 toast API(목록/읽음/삭제)가 담당하고, 여기는 전달 채널(기기)만 다룬다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-알림")
public class NotificationDeviceController {

    private final NotificationService notificationService;

    @Value("${push.vapid.public-key}")
    private String vapidPublicKey;

    @Operation(summary = "알림 수신 기기 등록",
            description = """
                    사용자가 "알림 켜기"를 누르고 브라우저 알림 권한을 허용하면 호출한다.
                    브라우저가 발급한 푸시 수신 정보(pushManager.subscribe() 결과의 toJSON())를 그대로 보낸다.
                    같은 기기를 다시 등록하면 갱신되며(중복 등록 없음), 한 회원이 여러 기기를 등록할 수 있다.

                    ### 요청 예시
                    ```json
                    {
                      "endpoint": "https://fcm.googleapis.com/fcm/send/abc...",
                      "keys": { "p256dh": "BNc...", "auth": "k8J..." }
                    }
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"알림 기기가 등록되었습니다."}
                    ```
                    실패: 400 {"message":"기기 등록 정보가 올바르지 않습니다."}
                    """)
    @PostMapping("/api/user/mypage/toast/devices")
    public ResponseEntity<Map<String, String>> registerDevice(@RequestBody NotificationDeviceRequest request) {
        notificationService.registerDevice(request);
        return ResponseEntity.ok(Map.of("message", "알림 기기가 등록되었습니다."));
    }

    @Operation(summary = "알림 수신 기기 해제",
            description = """
                    사용자가 "알림 끄기"를 누르거나 로그아웃할 때, 프론트가 pushManager 구독 해제와 함께 호출한다.
                    본인 소유 기기만 해제된다.

                    ### 요청 예시
                    ```json
                    {"endpoint": "https://fcm.googleapis.com/fcm/send/abc..."}
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"알림 기기가 해제되었습니다."}
                    ```
                    (이미 없는 기기여도 200 — 결과 상태는 동일)
                    """)
    @DeleteMapping("/api/user/mypage/toast/devices")
    public ResponseEntity<Map<String, String>> unregisterDevice(@RequestBody NotificationDeviceRequest request) {
        notificationService.unregisterDevice(request.getEndpoint());
        return ResponseEntity.ok(Map.of("message", "알림 기기가 해제되었습니다."));
    }

    @Operation(summary = "알림 발송 서버 공개키 (VAPID)",
            description = """
                    기기 등록 시 pushManager.subscribe 의 applicationServerKey 로 넣을 공개키.
                    값이 바뀌지 않으므로 프론트가 상수로 들고 있어도 되지만, 설정 어긋남 방지용으로 제공한다.

                    ### 응답 예시
                    ```json
                    {"publicKey":"BKdQZg..."}
                    ```
                    """)
    @GetMapping("/api/user/mypage/toast/vapid-key")
    public ResponseEntity<Map<String, String>> getVapidKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }
}
