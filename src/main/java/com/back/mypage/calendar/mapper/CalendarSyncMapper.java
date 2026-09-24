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

    /**
     * 재시도 대상 (FAILED 이고 아직 상한에 닿지 않은 것).
     *
     * @param maxRetry 이 횟수에 도달하면 더 시도하지 않는다
     * @param limit    한 번에 처리할 최대 건수 — 배치가 오래 끌지 않게 자른다
     */
    List<UserCalendarEvent> findRetryTargets(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    /** 재시도 상한에 걸려 더는 자동 복구하지 않는 건수 (사용자 안내용). */
    int countExhausted(@Param("userId") Long userId, @Param("maxRetry") int maxRetry);

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
