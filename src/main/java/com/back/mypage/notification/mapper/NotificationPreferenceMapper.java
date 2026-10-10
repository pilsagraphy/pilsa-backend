package com.back.mypage.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 회원별 알림 설정(user_notification_settings) + 글/댓글별 알림 끄기(notification_mutes).
 * 행이 없으면 "받음" 이다 — 설정을 한 번도 안 건드린 회원은 모든 알림을 받는다.
 */
@Mapper
public interface NotificationPreferenceMapper {

    /** 이 회원이 꺼 둔 알림 유형 이름 목록 (enabled = 0 인 행) */
    List<String> findDisabledTypes(@Param("userId") Long userId);

    /** 유형 스위치 저장 (없으면 insert, 있으면 update) */
    int upsertSetting(@Param("userId") Long userId, @Param("type") String type, @Param("enabled") boolean enabled);

    /** 이 회원이 이 대상의 알림을 껐는가 */
    boolean existsMute(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

    int insertMute(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

    int deleteMute(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);
}
