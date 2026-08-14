package com.back.event.controller;

import com.back.event.dto.*;
import com.back.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // 1. 일정 등록 (POST) - 201 Created (명세서 기준)
    @PostMapping("/api/admin/events")
    public ResponseEntity<EventResponse> createEvent(@RequestBody EventRequest request) {
        log.info("일정 등록 요청 데이터: {}", request);
        // 성공 시 201 상태코드와 함께 생성된 정보 및 메시지 반환
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(request));
    }

    // 2. 일정 수정 (PUT) - 200 OK
    @PutMapping("/api/admin/events/{scheduleId}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long scheduleId,
            @RequestBody EventUpdateRequest request) {
        log.info("일정 수정 요청 - ID: {}, 데이터: {}", scheduleId, request);
        return ResponseEntity.ok(eventService.updateEvent(scheduleId, request));
    }

    // 3. 일정 삭제 (DELETE) - 200 OK
    @DeleteMapping("/api/admin/events/{scheduleId}")
    public ResponseEntity<EventResponse> deleteEvent(@PathVariable Long scheduleId) {
        log.info("일정 삭제 요청 - ID: {}", scheduleId);
        return ResponseEntity.ok(eventService.deleteEvent(scheduleId));
    }

    // 4. 일정 목록 조회 (GET) - 200 OK
    @GetMapping("/api/events")
    public ResponseEntity<EventPageResponse> getEvents(
            @RequestParam("from") String from,
            @RequestParam("to") String to) {
        log.info("일정 목록 조회 요청 - 기간: {} ~ {}", from, to);
        return ResponseEntity.ok(eventService.getEventsByPeriod(from, to));
    }
}