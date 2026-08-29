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
import java.util.List;

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
                    일정 카드 클릭 시 펼치는 상세는 이 목록이 이미 주는 값으로 그리면 됩니다(추가 호출 불필요).
                    `GET /api/event/{eventId}`는 직접 링크·새로고침처럼 단건만 필요한 경우에 씁니다.""")
    @GetMapping("/api/event")
    public ResponseEntity<EventPageResponse> getEvents(
            @Parameter(description = "조회 시작 (YYYY-MM 또는 YYYY-MM-DD)", example = "2026-03") @RequestParam("from") String from,
            @Parameter(description = "조회 끝 (YYYY-MM 또는 YYYY-MM-DD)", example = "2026-03") @RequestParam("to") String to) {
        log.info("일정 목록 조회 요청 - 기간: {} ~ {}", from, to);
        return ResponseEntity.ok(eventService.getEventsByPeriod(from, to));
    }

    @Operation(summary = "일정 상세 조회 (비로그인 공개)",
            description = """
                    단건 일정 상세. 필드는 목록 조회(`GET /api/event`)와 동일 — 직접 링크·새로고침처럼
                    목록 없이 단건만 필요한 경우에 사용합니다.

                    ### 응답 예시
                    ```json
                    { "message": "일정 상세 정보를 성공적으로 불러왔습니다.",
                      "data": { "eventId": 1, "title": "가을 MT", "category": "정기모임",
                                "description": "일시/장소/준비물 ...", "startDate": "2026-10-20", "endDate": "2026-10-21" } }
                    ```
                    실패: 404(없거나 블라인드/삭제된 일정)""")
    @GetMapping("/api/event/{eventId}")
    public ResponseEntity<EventResponse> getEvent(
            @Parameter(description = "일정 ID") @PathVariable Long eventId) {
        log.info("일정 상세 조회 요청 - ID: {}", eventId);
        return ResponseEntity.ok(eventService.getEventById(eventId));
    }

    @Operation(summary = "일정 카테고리 목록 (비로그인 공개)",
            description = """
                    일정 등록/수정 화면의 카테고리 셀렉트박스용. event_categories 테이블이 정본이며,
                    등록/수정의 category 값은 이 목록에 있는 이름만 허용됩니다.

                    ### 응답 예시
                    ```json
                    [ { "eventCategoryId": 1, "name": "정기모임" }, { "eventCategoryId": 2, "name": "MT" } ]
                    ```""")
    @GetMapping("/api/event/categories")
    public ResponseEntity<List<EventCategoryResponse>> getEventCategories() {
        return ResponseEntity.ok(eventService.getEventCategories());
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

    @Operation(summary = "일정 1건 ICS (비로그인 공개)",
            description = """
                    일정 하나만 담긴 ICS 파일. **구독(calendar.ics)과 용도가 다릅니다.**

                    | | `/api/event/calendar.ics` | `/api/event/{eventId}.ics` |
                    |---|---|---|
                    | 성격 | 구독(subscribe) | 가져오기(import) |
                    | 이후 수정 반영 | 자동 반영 | **반영 안 됨** |
                    | 용도 | 전체 일정 구독 | 이 일정만 담기 |

                    ### 왜 필요한가
                    안드로이드에는 URL 구독 경로가 없습니다 — 구글 캘린더 앱에 "URL로 추가"가 없고(웹 전용),
                    `webcal://` 을 받아주는 기본 앱도 없습니다. 그래서 안드로이드에서는 전체 구독 대신
                    일정별 [내 캘린더에 담기] 로 대신합니다. iOS·데스크톱은 그대로 구독을 쓰면 됩니다.

                    ### 프론트 사용법
                    ```js
                    // 링크로 걸면 브라우저가 받아서 사용자의 캘린더 앱으로 넘긴다
                    location.href = `/api/event/${eventId}.ics`;
                    ```
                    `Content-Disposition: attachment` 라 브라우저가 바로 다운로드 흐름을 탑니다.

                    실패: 404 `{"message":"존재하지 않거나 삭제된 일정입니다."}` (삭제된 일정 포함)""")
    @GetMapping(value = "/api/event/{eventId}.ics")
    public ResponseEntity<byte[]> getEventFeed(
            @Parameter(description = "일정 ID", example = "32") @PathVariable("eventId") Long eventId) {
        log.info("단일 일정 ICS 요청 - eventId: {}", eventId);
        byte[] body = eventService.buildEventFeed(eventId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                // 구독 피드와 달리 attachment — 브라우저가 받아 캘린더 앱으로 넘기게 한다
                .header("Content-Disposition", "attachment; filename=\"pilsagraphy-event-" + eventId + ".ics\"")
                .body(body);
    }
}
