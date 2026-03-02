package com.back.schedule.mapper;

import com.back.schedule.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScheduleMapper {

    // 1. 일정 등록
    void insertSchedule(@Param("request") ScheduleRequest request, @Param("userId") Long userId);

    // 2. 일정 수정
    int updateSchedule(@Param("scheduleId") Long scheduleId, @Param("request") ScheduleUpdateRequest request);

    // 3. 일정 삭제
    int deleteSchedule(@Param("scheduleId") Long scheduleId);

    // 4. 일정 목록 조회
    List<ScheduleDataResponse> findSchedulesByPeriod(@Param("from") String from, @Param("to") String to);
}