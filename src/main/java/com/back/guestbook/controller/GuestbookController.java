package com.back.guestbook.controller;

import com.back.global.exception.BaseException;
import com.back.global.util.FileStorageUtil;
import com.back.guestbook.dto.GuestbookDrawingRow;
import com.back.guestbook.dto.GuestbookNoteDto;
import com.back.guestbook.dto.GuestbookNoteRequest;
import com.back.guestbook.dto.GuestbookResponse;
import com.back.guestbook.service.GuestbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

/**
 * 방명록 공개 API (PM 2026-10-10). /api/guestbook/** 는 SecurityConfig permitAll — 로그인 없이 읽고 남긴다.
 * 로그인 상태면 JWT 필터가 인증을 세워 두므로 글에 user_id 가 붙고, 그 글은 본인이 고치거나 지울 수 있다. 관리(숨김)는 /api/admin/guestbook/**.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "방명록 (공개)", description = "학기별 방명록 읽기·남기기·내 글 고치기/지우기·그림 스티커 이미지. 관리는 관리자-운영 관리-방명록 관리")
public class GuestbookController {

    private final GuestbookService service;
    private final FileStorageUtil fileStorageUtil;

    @Operation(summary = "방명록 (비로그인 공개)", description = """
            `semester` 를 비우면 이번 학기. 학기 탭(글이 있는 학기 + 이번 학기), 쓰기 제한값, 글 목록을 한 번에.
            ```json
            { "currentSemester": "2026-2학기", "semester": "2026-2학기", "semesters": ["2026-2학기", "2026-1학기"],
              "maxLength": 300, "maxDrawings": 3, "drawingMaxKb": 200,
              "notes": [ { "noteId": 12, "displayName": "지나가던 필사인", "isMine": false, "canManage": false, "state": "normal",
                  "content": "...", "font": "pen", "ink": "blueblack", "paper": "plain", "align": "left", "tilt": -4,
                  "drawings": [ { "drawingId": 3, "imageUrl": "/api/guestbook/drawings/3/image", "posX": 82.5, "posY": 18, "widthPct": 28, "rotation": 10 } ],
                  "createdAt": "2026-10-10T21:00:00", "updatedAt": "2026-10-10T21:00:00" } ] }
            ```
            font: pen/brush/gaegu/himelody/gamja/poor · ink: black/blueblack/sepia/burgundy/forest/pencil · paper: plain/lined/grid/cream/vintage ·
            align: left/center/right · tilt: 카드 기울기(도). drawings 의 posX/posY 는 카드 폭·높이 기준 %(그림 중심), widthPct 는 카드 폭 기준 %""")
    @GetMapping("/api/guestbook")
    public ResponseEntity<GuestbookResponse> get(@RequestParam(required = false) String semester) {
        return ResponseEntity.ok(service.getGuestbook(semester));
    }

    @Operation(summary = "방명록 남기기 (비로그인 가능)", description = """
            본문 `{ "displayName": "닉네임", "content": "...", "font": "pen", "ink": "black", "paper": "plain", "align": "left",
            "drawings": [ { "dataUrl": "data:image/png;base64,...", "posX": 80, "posY": 20, "widthPct": 30, "rotation": 0 } ] }`.
            로그인 상태면 user_id 가 붙어 나중에 고치거나 지울 수 있다(이름은 늘 자유 입력). 학기·기울기는 서버가 정한다. 201 과 함께 저장된 글.
            실패: 400(빈 내용·길이 초과·그림 수/크기 초과·PNG 아님), 429(같은 IP/회원이 guestbook_cooldown_seconds 안에 또 남김)""")
    @PostMapping("/api/guestbook")
    public ResponseEntity<GuestbookNoteDto> write(@RequestBody GuestbookNoteRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.write(request, clientIp(http)));
    }

    @Operation(summary = "내가 남긴 글 고치기 (로그인)", description = """
            본문은 남기기와 같다. drawings 에는 기존 그림을 `drawingId` 로(자리만 바꿈), 새 그림을 `dataUrl` 로 보내고,
            목록에서 빠진 그림은 떼어진다. 학기·기울기는 그대로. 남의 글·비로그인으로 남긴 글은 403""")
    @PutMapping("/api/guestbook/{noteId}")
    public ResponseEntity<GuestbookNoteDto> edit(@PathVariable Long noteId, @RequestBody GuestbookNoteRequest request) {
        return ResponseEntity.ok(service.edit(noteId, request));
    }

    @Operation(summary = "글 지우기 (로그인)", description = "내 글은 deleted(소프트). 관리자가 남의 글을 지우면 hidden 이라 복원할 수 있다. 그 외 403")
    @DeleteMapping("/api/guestbook/{noteId}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long noteId) {
        service.delete(noteId);
        return ResponseEntity.ok(Map.of("message", "지웠어요."));
    }

    @Operation(summary = "숨긴 글 복원 (관리자)", description = "관리자에게는 GET 응답에 숨긴 글(state=hidden)도 내려가므로 그 자리에서 되돌린다. 없으면 404")
    @PatchMapping("/api/guestbook/{noteId}/restore")
    public ResponseEntity<GuestbookNoteDto> restore(@PathVariable Long noteId) {
        return ResponseEntity.ok(service.restore(noteId));
    }

    @Operation(summary = "그림 스티커 이미지 (비로그인 공개)", description = "`drawings[].imageUrl` 이 가리키는 경로. 투명 PNG. 없으면 404")
    @GetMapping("/api/guestbook/drawings/{drawingId}/image")
    public ResponseEntity<Resource> drawingImage(@PathVariable Long drawingId) {
        GuestbookDrawingRow d = service.requireDrawing(drawingId);
        File file = fileStorageUtil.load(d.getFileUrl());
        if (file == null) throw new BaseException("그림이 없어요.", HttpStatus.NOT_FOUND);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(file.length())
                .header("X-Content-Type-Options", "nosniff")
                // 그림은 바뀌지 않는다(고치면 새 drawing_id) — 하루 캐시
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(new FileSystemResource(file));
    }

    /** nginx 뒤라 X-Forwarded-For 의 첫 주소가 실제 접속 주소다. 없으면 remoteAddr */
    private static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = http.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return http.getRemoteAddr();
    }
}
