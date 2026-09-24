package com.back.mypage.notification.controller;

import com.back.mypage.notification.mapper.NotificationDeviceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 푸시 구독 교체(rotation) 반영 — 서비스 워커가 부른다.
 *
 * 브라우저(FCM)는 구독을 이따금 갈아 끼운다(pushsubscriptionchange). 그러면 서버에 등록된 옛 endpoint 는 발송 때 410 이 나
 * 정리되고, 새 endpoint 는 앱을 다시 열어 로그인할 때까지 아무도 등록하지 않는다 — 그 사이 알림이 안 오고 마이페이지 토글은
 * 꺼진 것처럼 보인다 ("알림이 자꾸 차단돼요", 2026-09-24). 서비스 워커에는 로그인 토큰이 없으므로 이 경로는 인증 없이 열되,
 * **옛 endpoint 를 아는 쪽만** 바꿀 수 있다 — endpoint 는 추측 불가능한 긴 주소라 그 자체가 자격이다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-알림")
public class PushRotationController {

    private final NotificationDeviceMapper deviceMapper;

    @Getter
    @Setter
    public static class RotateRequest {
        private String oldEndpoint;
        private String endpoint;
        private Keys keys;

        @Getter
        @Setter
        public static class Keys {
            private String p256dh;
            private String auth;
        }
    }

    @Operation(summary = "푸시 구독 교체 반영 (서비스 워커 전용, 인증 없음)",
            description = "브라우저가 구독을 갈아 끼웠을 때 옛 endpoint 행을 새 구독으로 바꾼다. 옛 endpoint 가 등록돼 있지 않으면 아무것도 하지 않는다 (rotated=0).")
    @PutMapping("/api/auth/push/rotate")
    public ResponseEntity<Map<String, Object>> rotate(@RequestBody RotateRequest request) {
        if (request == null || isBlank(request.getOldEndpoint()) || isBlank(request.getEndpoint())
                || request.getKeys() == null || isBlank(request.getKeys().getP256dh()) || isBlank(request.getKeys().getAuth())
                || !request.getEndpoint().startsWith("https://")) {
            return ResponseEntity.badRequest().body(Map.of("message", "요청 값이 올바르지 않습니다."));
        }
        int rotated = deviceMapper.rotateEndpoint(request.getOldEndpoint(), request.getEndpoint(),
                request.getKeys().getP256dh(), request.getKeys().getAuth());
        if (rotated > 0) {
            log.info("푸시 구독 교체 반영 - rotated={}", rotated);
        }
        return ResponseEntity.ok(Map.of("rotated", rotated));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
