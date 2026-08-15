package com.back.event.mapper;

import com.back.event.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EventMapper {

    // 1. 일정 등록
    void insertEvent(@Param("request") EventRequest request, @Param("userId") Long userId);

    // 2. 일정 수정
    int updateEvent(@Param("eventId") Long eventId, @Param("request") EventUpdateRequest request);

    // 3. 일정 삭제
    int deleteEvent(@Param("eventId") Long eventId);

    // 4. 일정 목록 조회
    List<EventDataResponse> findEventsByPeriod(@Param("from") String from, @Param("to") String to);

    // 5. 일정 카테고리 목록 (is_active=1 만)
    List<EventCategoryResponse> findActiveEventCategories();

    // 6. 카테고리 유효성 (등록/수정 시 event_categories 에 있는 값만 허용)
    boolean existsEventCategory(@Param("name") String name);

    // 7. 캘린더 구독(ICS) 피드용 전체 일정
    List<EventCalendarRow> findAllForCalendar();
}