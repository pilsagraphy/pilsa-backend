package com.back.event.mapper;

import com.back.event.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EventMapper {

    // 1. 일정 목록 조회 (회원 달력)
    List<EventDataResponse> findEventsByPeriod(@Param("from") String from, @Param("to") String to);

    // 2. 캘린더 구독(ICS) 피드용 전체 일정
    List<EventCalendarRow> findAllForCalendar();

    // 3. 단일 일정 ICS 용 — 없거나 삭제된 일정이면 null
    EventCalendarRow findOneForCalendar(@Param("eventId") Long eventId);

    // 4. 일정 상세 조회
    EventDataResponse findEventById(@Param("eventId") Long eventId);

    // 5. 일정 카테고리 목록 (is_active=1 만)
    List<EventCategoryResponse> findActiveEventCategories();
}