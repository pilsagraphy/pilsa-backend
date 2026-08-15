package com.back.event.service;

import com.back.event.dto.*;

public interface EventService {

    // 1. 일정 목록 조회 (Public)
    EventPageResponse getEventsByPeriod(String from, String to);

    // 2. 구글 캘린더 구독용 iCalendar(ICS) 피드 (Public)
    //    한 번 구독하면 이후 등록/수정되는 일정이 구독자 캘린더에 자동 반영된다
    String buildCalendarFeed();
}
