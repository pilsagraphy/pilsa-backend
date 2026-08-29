package com.back.mypage.calendar.mapper;

import com.back.event.dto.EventCalendarRow;
import com.back.mypage.calendar.dto.UserCalendarEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CalendarSyncMapper {

    /** 동기화에 필요한 일정 한 건 (ICS 피드와 같은 포맷을 재사용한다). */
    EventCalendarRow findEventForSync(@Param("eventId") Long eventId);

    /** 연동 직후 초기 동기화 대상 — 아직 지나지 않은 일정만 넣는다. */
    List<EventCalendarRow> findUpcomingEventsForSync();

    UserCalendarEvent findMapping(@Param("userId") Long userId, @Param("eventId") Long eventId);

    List<UserCalendarEvent> findMappingsByUser(@Param("userId") Long userId);

    /** 일정 하나에 대한 전체 사용자 매핑 (일정 수정/삭제 팬아웃용). */
    List<UserCalendarEvent> findMappingsByEvent(@Param("eventId") Long eventId);

    int countByUserAndStatus(@Param("userId") Long userId, @Param("syncStatus") String syncStatus);

    int upsertMapping(UserCalendarEvent mapping);

    int markSynced(@Param("userId") Long userId,
                   @Param("eventId") Long eventId,
                   @Param("googleEventId") String googleEventId);

    int markFailed(@Param("userId") Long userId,
                   @Param("eventId") Long eventId,
                   @Param("lastError") String lastError);

    int markDeleted(@Param("userId") Long userId, @Param("eventId") Long eventId);

    int deleteByUserId(@Param("userId") Long userId);
}
