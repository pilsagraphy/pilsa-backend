package com.back.schedule.service;

import com.back.schedule.dto.*;
import com.back.schedule.exception.ScheduleException;
import com.back.schedule.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth; // 날짜 계산을 위해 필요
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleMapper scheduleMapper;

    // 관리자 권한 확인만 공통으로 사용
    private void checkAdminRole() {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new ScheduleException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest request) {
        checkAdminRole();

        // 날짜 선후 관계 검증 로직 추가
        validateExecutionDates(request.getStartDate(), request.getEndDate());

        // 등록 시에는 ERD 구조상 누가 등록했는지(user_id)가 필요하므로 가져옴
        String sub = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = Long.parseLong(sub);

        scheduleMapper.insertSchedule(request, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("scheduleId", request.getScheduleId());
        data.put("title", request.getTitle());

        return new ScheduleResponse("새로운 일정이 등록되었습니다.", data);
    }

    private void validateExecutionDates(String startDate, String endDate) {
        if (startDate == null || endDate == null) {
            throw new ScheduleException("시작일과 종료일은 필수 입력 항목입니다.", HttpStatus.BAD_REQUEST);
        }

        // 문자열을 비교 (YYYY-MM-DD 형식은 문자열 비교만으로도 선후 관계 확인 가능함)
        if (startDate.compareTo(endDate) > 0) {
            throw new ScheduleException("시작일이 종료일보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional
    public ScheduleResponse updateSchedule(Long scheduleId, ScheduleUpdateRequest request) {
        checkAdminRole(); // 권한만 확인

        int updated = scheduleMapper.updateSchedule(scheduleId, request);
        if (updated == 0) {
            throw new ScheduleException("해당 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("scheduleId", scheduleId);
        data.put("updatedAt", LocalDateTime.now());

        return new ScheduleResponse("일정 정보가 성공적으로 수정되었습니다.", data);
    }

    @Override
    @Transactional
    public ScheduleResponse deleteSchedule(Long scheduleId) {
        checkAdminRole(); // 권한만 확인

        int deleted = scheduleMapper.deleteSchedule(scheduleId);
        if (deleted == 0) {
            throw new ScheduleException("삭제할 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        return new ScheduleResponse("일정이 정상적으로 삭제되었습니다.", null);
    }

    @Override
    public SchedulePageResponse getSchedulesByPeriod(String from, String to) {
        // 2026-03로 와도 날짜를 특정해서 와도 처리 가능하도록
        // 7자리(2026-03)로 오면 10자리(2026-03-01 / 2026-03-31)로 변환
        String formattedFrom = formatToStartDate(from);
        String formattedTo = formatToEndDate(to);

        log.info("조회 기간 변환: {} ~ {} -> {} ~ {}", from, to, formattedFrom, formattedTo);

        List<ScheduleDataResponse> schedules = scheduleMapper.findSchedulesByPeriod(formattedFrom, formattedTo);
        return new SchedulePageResponse("일정 목록을 성공적으로 불러왔습니다.", schedules);
    }

    // 시작일 변환: 2026-03 -> 2026-03-01
    private String formatToStartDate(String date) {
        if (date.length() == 7) return date + "-01";
        return date;
    }

    // 종료일 변환: 2026-03 -> 2026-03-31 (해당 월의 실제 마지막 날 계산)
    private String formatToEndDate(String date) {
        if (date.length() == 7) {
            YearMonth yearMonth = YearMonth.parse(date);
            return date + "-" + yearMonth.lengthOfMonth(); // 28, 30, 31일을 정확히 계산
        }
        return date;
    }
    }