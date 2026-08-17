package com.back.mypage.notification.mapper;

import com.back.mypage.notification.dto.NotificationDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 알림 수신 기기 등록부 (notification_devices).
 * 세션성 데이터라 소프트삭제 대전제의 예외 — 물리 DELETE 를 쓴다.
 */
@Mapper
public interface NotificationDeviceMapper {

    /** 같은 endpoint 재등록은 UPSERT — 기기·브라우저당 1행, 한 회원이 여러 기기 가능 */
    void upsertDevice(@Param("userId") Long userId,
                      @Param("endpoint") String endpoint,
                      @Param("p256dh") String p256dh,
                      @Param("authSecret") String authSecret);

    /** 본인 기기만 해제 */
    int deleteByEndpoint(@Param("userId") Long userId, @Param("endpoint") String endpoint);

    List<NotificationDevice> findByUserId(@Param("userId") Long userId);

    /** 발송 응답이 404/410(수신 거부·앱 삭제)이면 즉시 정리 */
    void deleteById(@Param("deviceId") Long deviceId);

    /** 탈퇴 시 해당 회원의 모든 수신 기기 해제 (소프트삭제라 FK CASCADE 가 돌지 않음 — 수동 정리 필수) */
    int deleteByUserId(@Param("userId") Long userId);
}
