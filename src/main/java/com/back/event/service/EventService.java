package com.back.event.service;

import com.back.event.dto.*;

public interface EventService {

    // 1. 일정 등록 (Admin)
    // 새로운 일정 생성후 생성된 정보(ID, 제목)를 반환
    EventResponse createEvent(EventRequest request);

    //2. 일정 수정 (Admin)
    // 특정 일정의 정보를 수정.수정된 정보(ID, 수정일시)를 반환
    EventResponse updateEvent(Long scheduleId, EventUpdateRequest request);

    //3. 일정 삭제 (Admin)
    EventResponse deleteEvent(Long scheduleId);

    // 4. 일정 목록 조회 (Public)
    EventPageResponse getEventsByPeriod(String from, String to);
}