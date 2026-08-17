package com.back.mypage.notification.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 알림 수신 기기 등록/해제 요청.
 *
 * 프론트가 브라우저에서 발급받은 푸시 수신 정보(pushManager.subscribe() 결과의 toJSON())를
 * 그대로 보내는 형태다 — endpoint 가 "이 기기로 보내려면 이 주소로" 에 해당하는 수신 주소이고,
 * keys 는 페이로드 암호화에 쓰는 기기 측 키다. 해제(DELETE)는 endpoint 만 있으면 된다.
 */
@Getter
@Setter
public class NotificationDeviceRequest {

    private String endpoint;
    private Keys keys;

    @Getter
    @Setter
    public static class Keys {
        private String p256dh;
        private String auth;
    }
}
