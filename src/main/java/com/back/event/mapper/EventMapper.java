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
    int updateEvent(@Param("scheduleId") Long scheduleId, @Param("request") EventUpdateRequest request);

    // 3. 일정 삭제
    int deleteEvent(@Param("scheduleId") Long scheduleId);

    // 4. 일정 목록 조회
    List<EventDataResponse> findEventsByPeriod(@Param("from") String from, @Param("to") String to);
}