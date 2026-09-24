package com.back.mypage.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 알림 수신 동의 상태 (GET /api/user/mypage/toast/devices).
 *
 * 서버는 요청만 보고 "지금 이 기기"가 어느 행인지 알 수 없다(GET 에 본문이 없고, endpoint 를
 * 쿼리스트링에 실으면 푸시 주소가 액세스 로그에 남는다). 그래서 내 기기 목록을 주고,
 * 프론트가 자기 endpoint 와 대조해 토글 상태를 정한다:
 *
 *   const sub = await reg.pushManager.getSubscription();
 *   const on  = !!sub && devices.some(d => d.endpoint === sub.endpoint);
 *
 * 브라우저 구독이 살아 있어도 서버 행은 프론트 모르게 사라질 수 있다 —
 * 다른 기기에서 로그아웃했거나, 발송이 404/410 으로 실패해 서버가 자동 정리한 경우다.
 * 그때 토글이 켜진 채로 알림이 안 오는 상태를 프론트가 감지하려면 이 대조가 필요하다.
 */
@Getter
@Setter
public class NotificationDeviceStatusResponse {

    @Schema(description = "내가 알림 수신에 동의한 기기 수 (0이면 어느 기기에서도 알림을 받지 않는 상태)", example = "2")
    private int deviceCount;

    @Schema(description = "등록된 기기 목록. 암호화 키는 내려주지 않는다(발송 전용)")
    private List<NotificationDeviceSummary> devices;

    public NotificationDeviceStatusResponse(List<NotificationDeviceSummary> devices) {
        this.devices = devices;
        this.deviceCount = devices == null ? 0 : devices.size();
    }
}
