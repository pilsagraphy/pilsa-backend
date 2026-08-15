package com.back.event.controller;

import com.back.event.dto.*;
import com.back.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "일정(캘린더)", description = "동아리 일정 조회(비로그인 공개)와 구글 캘린더 구독 피드. 일정 등록/수정/삭제는 관리자-일정 관리 도메인 참고")
public class EventController {

    private final EventService eventService;

    @Operation(summary = "기간별 일정 목록 (비로그인 공개)",
            description = """
                    달력 화면용 일정 목록. `from`/`to` 는 YYYY-MM 또는 YYYY-MM-DD 둘 다 허용
                    (YYYY-MM 이면 서버가 그 달의 1일~말일로 변환).

                    ### 응답 예시
                    ```json
                    { "message": "일정 목록을 성공적으로 불러왔습니다.",
                      "data": [ { "eventId": 1, "title": "가을 MT", "category": "정기모임",
                                  "description": "일시/장소/준비물 ...", "startDate": "2026-10-20", "endDate": "2026-10-21" } ] }
                    ```
                    일정 카드 클릭 시 펼치는 상세는 이 목록의 description 으로 그리면 됩니다(추가 호출 불필요).""")
    @GetMapping("/api/event")
    public ResponseEntity<EventPageResponse> getEvents(
            @Parameter(description = "조회 시작 (YYYY-MM 또는 YYYY-MM-DD)", example = "2026-03") @RequestParam("from") String from,
            @Parameter(description = "조회 끝 (YYYY-MM 또는 YYYY-MM-DD)", example = "2026-03") @RequestParam("to") String to) {
        log.info("일정 목록 조회 요청 - 기간: {} ~ {}", from, to);
        return ResponseEntity.ok(eventService.getEventsByPeriod(from, to));
    }

    @Operation(summary = "구글 캘린더 구독 피드 (ICS, 비로그인 공개)",
            description = """
                    iCalendar(ICS) 형식의 일정 피드. 한 번 구독하면 이후 등록/수정/삭제되는 모든 일정이
                    구독자의 구글 캘린더에 자동 반영됩니다(구글이 주기적으로 이 URL을 다시 읽음 — 수 시간~하루).

                    ### 프론트 [구독하기] 버튼 구현
                    ```js
                    const feed = 'https://{서비스 도메인}/api/event/calendar.ics';
                    window.open('https://calendar.google.com/calendar/render?cid=' + encodeURIComponent(feed));
                    ```
                    구글 서버가 인증 없이 피드를 가져가므로 이 API 는 PUBLIC 입니다.
                    애플/아웃룩 캘린더도 같은 URL 로 구독 가능합니다.""")
    @GetMapping(value = "/api/event/calendar.ics")
    public ResponseEntity<byte[]> getCalendarFeed() {
        byte[] body = eventService.buildCalendarFeed().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .header("Content-Disposition", "inline; filename=\"pilsagraphy.ics\"")
                .body(body);
    }
}
