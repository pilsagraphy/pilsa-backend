package com.back.mypage.calendar.mapper;

import com.back.mypage.calendar.dto.KakaoCalendarLink;
import com.back.mypage.calendar.dto.UserCalendarEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 톡캘린더 연동(user_kakao_calendar) + 사용자별 톡캘린더 일정 매핑(user_kakao_calendar_events). 구글의 CalendarSyncMapper 와 같은 모양 */
@Mapper
public interface KakaoCalendarMapper {

    KakaoCalendarLink findByUserId(@Param("userId") Long userId);

    List<KakaoCalendarLink> findAllLinked();

    int upsertLink(KakaoCalendarLink link);

    int updateTokens(@Param("userId") Long userId, @Param("accessToken") byte[] accessToken, @Param("expiresIn") int expiresIn,
                     @Param("refreshToken") byte[] refreshToken);

    int updateLastSyncedAt(@Param("userId") Long userId);

    int deleteByUserId(@Param("userId") Long userId);

    // ── 매핑 (googleEventId 자리에 kakao event_id 를 담는다 — DTO 를 그대로 쓴다)
    UserCalendarEvent findMapping(@Param("userId") Long userId, @Param("eventId") Long eventId);

    List<UserCalendarEvent> findMappingsByUser(@Param("userId") Long userId);

    List<UserCalendarEvent> findMappingsByEvent(@Param("eventId") Long eventId);

    List<UserCalendarEvent> findRetryTargets(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    int countExhausted(@Param("userId") Long userId, @Param("maxRetry") int maxRetry);

    int upsertMapping(UserCalendarEvent mapping);

    int markSynced(@Param("userId") Long userId, @Param("eventId") Long eventId, @Param("kakaoEventId") String kakaoEventId);

    int markFailed(@Param("userId") Long userId, @Param("eventId") Long eventId, @Param("lastError") String lastError);

    int markDeleted(@Param("userId") Long userId, @Param("eventId") Long eventId);

    int deleteMappingsByUserId(@Param("userId") Long userId);
}
