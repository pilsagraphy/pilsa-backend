package com.back.schedule.service;

import com.back.schedule.dto.*;

public interface ScheduleService {

    // 1. 일정 등록 (Admin)
    // 새로운 일정 생성후 생성된 정보(ID, 제목)를 반환
    ScheduleResponse createSchedule(ScheduleRequest request);

    //2. 일정 수정 (Admin)
    // 특정 일정의 정보를 수정.수정된 정보(ID, 수정일시)를 반환
    ScheduleResponse updateSchedule(Long scheduleId, ScheduleUpdateRequest request);

    //3. 일정 삭제 (Admin)
    ScheduleResponse deleteSchedule(Long scheduleId);

    // 4. 일정 목록 조회 (Public)
    SchedulePageResponse getSchedulesByPeriod(String from, String to);
}