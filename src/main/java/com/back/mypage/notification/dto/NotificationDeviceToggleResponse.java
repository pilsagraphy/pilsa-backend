package com.back.mypage.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 알림 수신 동의/거부 처리 결과 (PUT /api/user/mypage/toast/devices).
 * 처리 후 상태를 함께 내려주므로 프론트가 목록을 재조회할 필요가 없다.
 */
@Getter
public class NotificationDeviceToggleResponse {

    @Schema(description = "이 기기의 수신 동의 여부 (요청한 enabled 가 그대로 반영된 결과)", example = "true")
    private final Boolean enabled;

    @Schema(description = "처리 후 내가 알림 수신에 동의한 전체 기기 수", example = "2")
    private final int deviceCount;

    @Schema(description = "사용자에게 보여줄 안내 문구", example = "이 기기로 알림을 받습니다.")
    private final String message;

    public NotificationDeviceToggleResponse(Boolean enabled, int deviceCount, String message) {
        this.enabled = enabled;
        this.deviceCount = deviceCount;
        this.message = message;
    }
}
