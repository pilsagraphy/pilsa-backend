package com.back.mypage.notification.mapper;

import com.back.mypage.notification.dto.NotificationCreate;
import com.back.mypage.notification.dto.NotificationReadResponse;
import com.back.mypage.notification.dto.NotificationResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 알림 매퍼 — 발행(INSERT) + 알림함(목록/뱃지/읽음/삭제).
 *
 * 모든 알림함 쿼리는 소유권을 위해 반드시 user_id 조건을 함께 건다(남의 알림 접근 차단).
 * user_id 는 요청이 아니라 AuthUtils.currentUserId() 에서 온다.
 */
@Mapper
public interface NotificationMapper {

    /**
     * 알림 1건 발행. useGeneratedKeys 로 command.notificationId 가 채워진다.
     * 발행 정책(누구에게 줄지)은 호출 도메인이 정하고, 여기는 저장만 한다.
     */
    void insertNotification(NotificationCreate command);

    /** 목록 — 최근 N개월(months), state=normal, 최신순. boardId 는 posts 조인으로 유도 */
    List<NotificationResponse> findByUser(@Param("userId") Long userId, @Param("months") int months);

    /** 미읽음 개수 (뱃지·응답 공용). 목록과 동일하게 최근 N개월 창 기준 */
    int countUnread(@Param("userId") Long userId, @Param("months") int months);

    /** 단건 읽음 — 본인 normal 미읽음만. 이미 읽었으면 0행(멱등은 서비스가 SELECT 로 보장) */
    int markAsRead(@Param("toastId") Long toastId, @Param("userId") Long userId);

    /** 읽음 응답용 이동정보 — 본인 normal 알림이 아니면 null → 404 판정 */
    NotificationReadResponse findMoveInfo(@Param("toastId") Long toastId, @Param("userId") Long userId);

    /** 전체 읽음 — 본인 normal 미읽음(최근 N개월). 처리 행 수 반환 */
    int markAllAsRead(@Param("userId") Long userId, @Param("months") int months);

    /** 단건 삭제(소프트) — 본인 normal 만. 0행이면 없음/남의것/이미삭제 → 404 */
    int softDelete(@Param("toastId") Long toastId, @Param("userId") Long userId);

    /** 목록 표시 기간(개월) — policy_settings.notification_list_months. 없으면 null */
    Integer findListMonths();

    /** 탈퇴 시 본인 알림 일괄 정리 (수신자 본인만 보는 데이터라 증적 가치 없음) */
    int softDeleteAllByUser(@Param("userId") Long userId);

    /** policy_settings 값 조회 (notification_retention_days 등) */
    String findPolicySetting(@Param("code") String code);

    /** 보존기간이 지난 알림 물리 삭제 — 새벽 정리 배치 전용 */
    int deleteExpiredNotifications(@Param("retentionDays") int retentionDays);
}
