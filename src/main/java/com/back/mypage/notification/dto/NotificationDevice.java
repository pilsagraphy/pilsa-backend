package com.back.mypage.notification.dto;

import lombok.Getter;
import lombok.Setter;

// 알림 수신 기기 한 대 (notification_devices 행) — 발송 시 조회용
@Getter
@Setter
public class NotificationDevice {
    private Long deviceId;
    private String endpoint;
    private String p256dh;
    private String authSecret;
}
