package com.back.mypage.notification.controller;

import com.back.mypage.notification.dto.NotificationDeviceRequest;
import com.back.mypage.notification.dto.NotificationDeviceStatusResponse;
import com.back.mypage.notification.dto.NotificationDeviceToggleResponse;
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
 * 알림 수신 동의 관리 (웹앱·모바일 브라우저의 토스트 알림용).
 *
 * "이 기기로도 알림을 보내 달라"는 등록부를 관리한다 — 일정관리의 캘린더 구독과는 무관하다.
 * 알림 자체는 기존 toast API(목록/읽음/삭제)가 담당하고, 여기는 전달 채널(기기)만 다룬다.
 *
 * 동의(등록)와 거부(해제)를 별도 API 로 두지 않고 PUT 하나로 합쳤다 — 프론트가 서버 상태를
 * 몰라도 원하는 상태만 보내면 되고, 상태 판정은 GET 으로 따로 확인한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-알림")
public class NotificationDeviceController {

    private final NotificationService notificationService;

    @Value("${push.vapid.public-key}")
    private String vapidPublicKey;

    @Operation(summary = "알림 수신 동의 상태 조회",
            description = """
                    알림 토글을 그리기 전에 호출한다. 내가 알림 수신에 동의한 기기 목록을 내려준다.

                    프론트는 이 목록을 자기 브라우저의 구독 주소와 대조해 토글 상태를 정한다:
                    ```js
                    const sub = await reg.pushManager.getSubscription();
                    const { devices } = await api.get('/api/user/mypage/toast/devices');
                    const on = !!sub && devices.some(d => d.endpoint === sub.endpoint);
                    ```
                    브라우저 구독이 살아 있어도 서버 행은 프론트 모르게 사라질 수 있다 —
                    다른 기기에서 로그아웃했거나, 발송이 404/410 으로 실패해 서버가 자동 정리한 경우다.
                    이때 대조 없이 브라우저 상태만 믿으면 "토글은 켜져 있는데 알림이 안 오는" 화면이 된다.

                    ### 요청 예시
                    ```
                    GET /api/user/mypage/toast/devices
                    ```
                    (쿼리 파라미터 없음)

                    ### 응답 예시
                    ```json
                    {
                      "deviceCount": 2,
                      "devices": [
                        {"endpoint":"https://fcm.googleapis.com/fcm/send/abc...","registeredAt":"2026-08-17 09:14:02"},
                        {"endpoint":"https://web.push.apple.com/xyz...","registeredAt":"2026-08-15 21:03:44"}
                      ]
                    }
                    ```
                    deviceCount 가 0이면 어느 기기에서도 알림을 받지 않는 상태다.
                    암호화 키(p256dh/auth)는 발송 전용이라 내려주지 않는다.
                    """)
    @GetMapping("/api/user/mypage/toast/devices")
    public ResponseEntity<NotificationDeviceStatusResponse> getDeviceStatus() {
        return ResponseEntity.ok(notificationService.getDeviceStatus());
    }

    @Operation(summary = "알림 수신 동의/거부 (토글)",
            description = """
                    알림 토글을 켜거나 끌 때 호출한다. 등록·해제가 하나의 API 로 합쳐져 있어
                    프론트는 현재 서버 상태를 몰라도 "원하는 상태"만 보내면 된다. 같은 요청을 두 번 보내도 결과가 같다.

                    - **켤 때(enabled=true)**: 브라우저 알림 권한을 허용받고 `pushManager.subscribe()` 한 결과의
                      `toJSON()` 을 endpoint·keys 로 그대로 보낸다. 같은 기기 재등록은 갱신되며(중복 없음),
                      한 회원이 여러 기기를 등록할 수 있다.
                    - **끌 때(enabled=false)**: keys 는 필요 없다. 해당 기기 행을 물리 삭제한다
                      (notification_devices 는 세션성 데이터라 소프트삭제 예외).
                      본인 소유 기기만 해제되고, 다른 기기의 수신 설정은 그대로 유지된다.
                    - **로그아웃 시에도 enabled=false 로 호출한다** — 호출하지 않으면 그 기기로 알림이 계속 가고,
                      공용 기기에서는 남의 알림 내용이 뜬다.

                    ### 프론트 주의 — 토글 OFF 와 로그아웃은 브라우저 쪽 처리가 다르다
                    | | 서버 기기 행 | `subscription.unsubscribe()` |
                    |---|---|---|
                    | 사용자가 토글 OFF | 삭제 | **한다** |
                    | 로그아웃 | 삭제 | **하지 않는다** |

                    구독이 남아 있는 것이 "사용자가 알림을 끈 게 아니다"의 유일한 근거다. 로그아웃에서
                    unsubscribe 까지 하면 재로그인 시 복구할 근거가 없어져 **로그인할 때마다 알림이 꺼진 채**가 된다.
                    재로그인 직후 구독이 살아 있으면 이 API 를 enabled=true 로 한 번 호출해 조용히 되살린다
                    (권한이 이미 granted 라 사용자에게 다시 묻지 않는다). 절차는 HANDOFF-notification.md 3-2-1·3-2-2.

                    ### 요청 예시 (동의)
                    ```json
                    {
                      "enabled": true,
                      "endpoint": "https://fcm.googleapis.com/fcm/send/abc...",
                      "keys": { "p256dh": "BNc...", "auth": "k8J..." }
                    }
                    ```

                    ### 요청 예시 (거부)
                    ```json
                    {
                      "enabled": false,
                      "endpoint": "https://fcm.googleapis.com/fcm/send/abc..."
                    }
                    ```

                    ### 응답 예시
                    ```json
                    {"enabled":true,"deviceCount":2,"message":"이 기기로 알림을 받습니다."}
                    ```
                    거부 시: `{"enabled":false,"deviceCount":1,"message":"이 기기에서는 알림을 받지 않습니다."}`
                    처리 후 상태를 함께 주므로 목록을 재조회할 필요가 없다.

                    실패: 400 {"message":"enabled 값이 필요합니다. (true=수신 동의, false=수신 거부)"}
                         400 {"message":"기기 endpoint 가 필요합니다."}
                         400 {"message":"기기 등록 정보가 올바르지 않습니다."} (enabled=true 인데 keys 누락)
                    """)
    @PutMapping("/api/user/mypage/toast/devices")
    public ResponseEntity<NotificationDeviceToggleResponse> setDeviceEnabled(
            @RequestBody NotificationDeviceRequest request) {
        return ResponseEntity.ok(notificationService.setDeviceEnabled(request));
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
