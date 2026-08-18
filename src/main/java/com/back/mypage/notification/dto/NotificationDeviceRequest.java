package com.back.mypage.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 알림 수신 동의/거부 요청 (PUT /api/user/mypage/toast/devices).
 *
 * 등록·해제를 따로 두지 않고 enabled 한 값으로 합쳤다 — 프론트는 현재 서버 상태를 몰라도
 * "원하는 상태"만 보내면 되고, 같은 요청을 두 번 보내도 결과가 같다(멱등).
 *
 * endpoint 는 "이 기기로 보내려면 이 주소로" 에 해당하는 브라우저 발급 수신 주소이고,
 * keys 는 페이로드 암호화에 쓰는 기기 측 키다(pushManager.subscribe() 결과의 toJSON() 그대로).
 * 거부(enabled=false)는 대상 기기를 특정할 endpoint 만 있으면 되고 keys 는 필요 없다.
 */
@Getter
@Setter
public class NotificationDeviceRequest {

    // Boolean 래퍼 사용: primitive 면 미전달 시 false 로 조용히 채워져 "거부"로 오해된다 → null 검증 필요
    @Schema(description = "true=이 기기로 알림 받기(동의), false=받지 않기(거부)", example = "true")
    private Boolean enabled;

    @Schema(description = "브라우저가 발급한 푸시 수신 주소", example = "https://fcm.googleapis.com/fcm/send/abc...")
    private String endpoint;

    @Schema(description = "페이로드 암호화 키. enabled=true 일 때만 필수")
    private Keys keys;

    @Getter
    @Setter
    public static class Keys {
        @Schema(example = "BNc...")
        private String p256dh;
        @Schema(example = "k8J...")
        private String auth;
    }
}
