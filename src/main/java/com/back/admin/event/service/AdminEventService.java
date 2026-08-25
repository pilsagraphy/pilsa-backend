package com.back.admin.event.service;

import com.back.event.dto.EventRequest;
import com.back.event.dto.EventResponse;
import com.back.event.dto.EventUpdateRequest;
import com.back.event.exception.EventException;
import com.back.admin.event.mapper.AdminEventMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 일정 관리(관리자) — 등록/수정/삭제.
 *
 * 회원 달력 조회·캘린더 구독 피드는 com.back.event 가 담당하고, 여기는 관리자 화면 전용이다.
 * 매퍼도 이 패키지에서 직접 관리한다({@link AdminEventMapper}) — 관리자 쿼리와 회원 조회 쿼리는 겹치지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEventService {

    private final AdminEventMapper adminEventMapper;

    // 관리자 권한 확인 (공통 유틸 사용)
    private void checkAdminRole() {
        if (!AuthUtils.isAdmin()) {
            throw new EventException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    // 카테고리 유효성 검증 — event_categories 에 등록된 값만 허용 (자유 입력 폐지)
    private void validateCategory(String category) {
        if (category != null && !category.isBlank() && !adminEventMapper.existsEventCategory(category)) {
            throw new EventException("유효하지 않은 일정 카테고리입니다. (GET /api/event/categories 참고)", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public EventResponse createEvent(EventRequest request) {
        checkAdminRole();

        // 날짜 선후 관계 검증
        validateExecutionDates(request.getStartDate(), request.getEndDate());
        validateCategory(request.getCategory());

        // 등록 시에는 ERD 구조상 누가 등록했는지(user_id)가 필요하므로 가져옴
        Long userId = AuthUtils.currentUserId();

        adminEventMapper.insertEvent(request, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", request.getEventId());
        data.put("title", request.getTitle());

        return new EventResponse("새로운 일정이 등록되었습니다.", data);
    }

    private void validateExecutionDates(String startDate, String endDate) {
        if (startDate == null || endDate == null) {
            throw new EventException("시작일과 종료일은 필수 입력 항목입니다.", HttpStatus.BAD_REQUEST);
        }

        // 문자열을 비교 (YYYY-MM-DD 형식은 문자열 비교만으로도 선후 관계 확인 가능함)
        if (startDate.compareTo(endDate) > 0) {
            throw new EventException("시작일이 종료일보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, EventUpdateRequest request) {
        checkAdminRole(); // 권한만 확인
        validateCategory(request.getCategory());

        int updated = adminEventMapper.updateEvent(eventId, request);
        if (updated == 0) {
            throw new EventException("해당 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("updatedAt", LocalDateTime.now());

        return new EventResponse("일정 정보가 성공적으로 수정되었습니다.", data);
    }

    @Transactional
    public EventResponse deleteEvent(Long eventId) {
        checkAdminRole(); // 권한만 확인

        int deleted = adminEventMapper.deleteEvent(eventId);
        if (deleted == 0) {
            throw new EventException("삭제할 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        return new EventResponse("일정이 정상적으로 삭제되었습니다.", null);
    }
}
