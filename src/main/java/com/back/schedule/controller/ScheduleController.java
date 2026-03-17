package com.back.schedule.controller;

import com.back.schedule.dto.*;
import com.back.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // 1. 일정 등록 (POST) - 201 Created (명세서 기준)
    @PostMapping("/api/admin/schedules")
    public ResponseEntity<ScheduleResponse> createSchedule(@RequestBody ScheduleRequest request) {
        log.info("일정 등록 요청 데이터: {}", request);
        // 성공 시 201 상태코드와 함께 생성된 정보 및 메시지 반환
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleService.createSchedule(request));
    }

    // 2. 일정 수정 (PUT) - 200 OK
    @PutMapping("/api/admin/schedules/{scheduleId}")
    public ResponseEntity<ScheduleResponse> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody ScheduleUpdateRequest request) {
        log.info("일정 수정 요청 - ID: {}, 데이터: {}", scheduleId, request);
        return ResponseEntity.ok(scheduleService.updateSchedule(scheduleId, request));
    }

    // 3. 일정 삭제 (DELETE) - 200 OK
    @DeleteMapping("/api/admin/schedules/{scheduleId}")
    public ResponseEntity<ScheduleResponse> deleteSchedule(@PathVariable Long scheduleId) {
        log.info("일정 삭제 요청 - ID: {}", scheduleId);
        return ResponseEntity.ok(scheduleService.deleteSchedule(scheduleId));
    }

    // 4. 일정 목록 조회 (GET) - 200 OK
    @GetMapping("/api/public/schedules")
    public ResponseEntity<SchedulePageResponse> getSchedules(
            @RequestParam("from") String from,
            @RequestParam("to") String to) {
        log.info("일정 목록 조회 요청 - 기간: {} ~ {}", from, to);
        return ResponseEntity.ok(scheduleService.getSchedulesByPeriod(from, to));
    }
}