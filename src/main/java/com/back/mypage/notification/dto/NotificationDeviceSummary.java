package com.back.mypage.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 등록된 알림 수신 기기 한 대 (화면 응답용).
 * 발송용 {@link NotificationDevice} 와 달리 암호화 키(p256dh/auth_secret)는 담지 않는다 —
 * 클라이언트가 알 필요가 없고, 유출되면 해당 기기로 임의 푸시를 보낼 수 있게 된다.
 */
@Getter
@Setter
public class NotificationDeviceSummary {

    @Schema(description = "푸시 수신 주소. 프론트가 자기 subscription.endpoint 와 대조해 이 기기인지 판정한다",
            example = "https://fcm.googleapis.com/fcm/send/abc...")
    private String endpoint;

    @Schema(description = "수신 동의 일시", example = "2026-08-17 09:14:02")
    private String registeredAt;
}
