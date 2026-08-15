package com.back.event.service;

import com.back.event.dto.*;

public interface EventService {

    // 1. 일정 등록 (Admin)
    // 새로운 일정 생성후 생성된 정보(ID, 제목)를 반환
    EventResponse createEvent(EventRequest request);

    //2. 일정 수정 (Admin)
    // 특정 일정의 정보를 수정.수정된 정보(ID, 수정일시)를 반환
    EventResponse updateEvent(Long eventId, EventUpdateRequest request);

    //3. 일정 삭제 (Admin)
    EventResponse deleteEvent(Long eventId);

    // 4. 일정 목록 조회 (Public)
    EventPageResponse getEventsByPeriod(String from, String to);

    // 5. 일정 카테고리 목록 (Public) — 등록/수정 화면 셀렉트박스용
    java.util.List<EventCategoryResponse> getEventCategories();

    // 6. 구글 캘린더 구독용 iCalendar(ICS) 피드 (Public)
    //    한 번 구독하면 이후 등록/수정되는 일정이 구독자 캘린더에 자동 반영된다
    String buildCalendarFeed();
}