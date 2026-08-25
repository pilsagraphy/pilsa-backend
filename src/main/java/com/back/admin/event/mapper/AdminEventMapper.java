package com.back.admin.event.mapper;

import com.back.event.dto.EventRequest;
import com.back.event.dto.EventUpdateRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 일정 관리(관리자) 매퍼 — 등록/수정/삭제.
 * 회원 달력 조회·캘린더 피드 쿼리는 event 도메인의 EventMapper 가 담당한다 (쿼리 중복 없음).
 */
@Mapper
public interface AdminEventMapper {

    // 1. 일정 등록
    void insertEvent(@Param("request") EventRequest request, @Param("userId") Long userId);

    // 2. 일정 수정 (전달된 필드만)
    int updateEvent(@Param("eventId") Long eventId, @Param("request") EventUpdateRequest request);

    // 3. 일정 삭제 (소프트)
    int deleteEvent(@Param("eventId") Long eventId);

    // 4. 카테고리 유효성 (등록/수정 시 event_categories 에 있는 값만 허용)
    boolean existsEventCategory(@Param("name") String name);
}
