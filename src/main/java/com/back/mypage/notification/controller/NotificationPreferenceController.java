package com.back.mypage.notification.controller;

import com.back.mypage.notification.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 회원 알림 설정 — 유형 스위치(마이페이지 설정) + 글/댓글별 알림 끄기 (PM 2026-10-10).
 * 알림함·수신 기기는 {@link NotificationController}, {@link NotificationDeviceController} 가 맡는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/mypage")
@Tag(name = "마이페이지-알림 설정")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    @Operation(summary = "내 알림 유형 설정", description = """
            마이페이지 > 설정 > 알림. 회원이 고를 수 있는 네 유형의 켜짐 여부. 행이 없으면 켜짐.
            관리자가 운영 관리 > 알림 설정에서 끈 유형은 여기서 켜 둬도 오지 않는다.
            ```json
            { "settings": { "COMMENT": true, "REPLY": true, "PINNED_POST": false, "EVENT": true } }
            ```""")
    @GetMapping("/notification-settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return ResponseEntity.ok(Map.of("settings", service.mySettings()));
    }

    @Operation(summary = "알림 유형 켜기/끄기", description = """
            경로의 type 은 COMMENT / REPLY / PINNED_POST / EVENT. 본문 `{ "enabled": false }`. 바뀐 전체 설정을 돌려준다.
            그 밖의 유형(신고 처리·제재)은 400.""")
    @PutMapping("/notification-settings/{type}")
    public ResponseEntity<Map<String, Object>> updateSetting(@PathVariable String type,
                                                             @RequestBody SettingRequest request) {
        return ResponseEntity.ok(Map.of("settings", service.updateSetting(type, request.isEnabled())));
    }

    @Operation(summary = "글/댓글 알림 끔 여부", description = """
            `?targetType=post&targetId=12` — post 는 "이 글의 댓글 알림", comment 는 "이 댓글의 답글 알림".
            ```json
            { "muted": true }
            ```""")
    @GetMapping("/notification-mutes")
    public ResponseEntity<Map<String, Object>> getMute(@RequestParam String targetType, @RequestParam Long targetId) {
        return ResponseEntity.ok(Map.of("muted", service.isMutedByMe(targetType, targetId)));
    }

    @Operation(summary = "글/댓글 알림 끄기/켜기", description = """
            본문 `{ "targetType": "post", "targetId": 12, "muted": true }`. 바뀐 상태를 돌려준다.
            내 글이 아니어도 저장은 되지만 알림 자체가 글·댓글 작성자에게만 가므로 효과는 작성자에게만 있다.""")
    @PutMapping("/notification-mutes")
    public ResponseEntity<Map<String, Object>> setMute(@RequestBody MuteRequest request) {
        return ResponseEntity.ok(Map.of("muted", service.setMute(request.getTargetType(), request.getTargetId(), request.isMuted())));
    }

    @Getter
    @Setter
    public static class SettingRequest {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class MuteRequest {
        private String targetType;
        private Long targetId;
        private boolean muted = true;
    }
}
