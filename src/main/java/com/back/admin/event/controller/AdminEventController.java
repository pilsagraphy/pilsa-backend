package com.back.admin.event.controller;

import com.back.admin.event.service.AdminEventService;
import com.back.event.dto.EventRequest;
import com.back.event.dto.EventResponse;
import com.back.event.dto.EventUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-일정 관리", description = "일정달력 관리 화면 — 일정 등록/수정/삭제. 회원 달력 조회와 캘린더 구독 피드는 '일정(캘린더)' 도메인 참고")
public class AdminEventController {

    private final AdminEventService adminEventService;

    @Operation(summary = "일정 등록 (관리자)",
            description = """
                    일정달력 관리 화면의 (+) 버튼으로 일정을 등록합니다. 성공 시 **201 Created**.

                    ### 요청 예시
                    ```json
                    { "title": "3월 정기모임", "category": "정기모임",
                      "description": "3월 정기모임 안내", "startDate": "2026-03-01", "endDate": "2026-03-01" }
                    ```
                    - category 는 **관리자 자유 입력**(events.category varchar(50), 예: 정기모임) — 선택지 목록 API 없음.
                      카테고리 테이블(event_categories)은 2026-08-16 구현했다가 PM 지시로 당일 롤백(DB 드랍)했고,
                      완성본은 git 브랜치 `archive/event-categories`(da6da1d, 원격 보관)에 있다. 되살릴 땐 체리픽할 것.
                      (`categories` 테이블은 게시판 전용이라 일정과 무관 — 재사용 금지)
                    - description 은 필수(DB NOT NULL), 날짜는 YYYY-MM-DD

                    ### 응답 예시
                    ```json
                    { "message": "새로운 일정이 등록되었습니다.", "data": { "eventId": 12, "title": "3월 정기모임" } }
                    ```
                    실패: 400(필수값/날짜 역전), 403(관리자 아님)""")
    @PostMapping("/api/admin/event")
    public ResponseEntity<EventResponse> createEvent(@RequestBody EventRequest request) {
        log.info("일정 등록 요청 데이터: {}", request);
        // 성공 시 201 상태코드와 함께 생성된 정보 및 메시지 반환
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminEventService.createEvent(request));
    }

    @Operation(summary = "일정 수정 (관리자)",
            description = """
                    전달한 필드만 수정됩니다(전부 선택).

                    ### 요청 예시
                    ```json
                    { "title": "3월 정기모임(장소 변경)", "description": "장소가 변경되었습니다." }
                    ```
                    ### 응답 예시
                    ```json
                    { "message": "일정 정보가 성공적으로 수정되었습니다.", "data": { "eventId": 12, "updatedAt": "..." } }
                    ```
                    실패: 404(없거나 삭제된 일정), 403(관리자 아님)""")
    @PutMapping("/api/admin/event/{eventId}")
    public ResponseEntity<EventResponse> updateEvent(
            @Parameter(description = "일정 ID") @PathVariable Long eventId,
            @RequestBody EventUpdateRequest request) {
        log.info("일정 수정 요청 - ID: {}, 데이터: {}", eventId, request);
        return ResponseEntity.ok(adminEventService.updateEvent(eventId, request));
    }

    @Operation(summary = "일정 삭제 (관리자)",
            description = "소프트삭제(state=deleted)입니다. 응답: `{\"message\":\"일정이 정상적으로 삭제되었습니다.\"}` / 실패: 404")
    @DeleteMapping("/api/admin/event/{eventId}")
    public ResponseEntity<EventResponse> deleteEvent(
            @Parameter(description = "일정 ID") @PathVariable Long eventId) {
        log.info("일정 삭제 요청 - ID: {}", eventId);
        return ResponseEntity.ok(adminEventService.deleteEvent(eventId));
    }
}
