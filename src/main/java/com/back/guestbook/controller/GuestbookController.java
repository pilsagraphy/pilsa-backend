package com.back.guestbook.controller;

import com.back.global.exception.BaseException;
import com.back.global.util.FileStorageUtil;
import com.back.guestbook.dto.GuestbookNoteDto;
import com.back.guestbook.dto.GuestbookNoteRequest;
import com.back.guestbook.dto.GuestbookResponse;
import com.back.guestbook.dto.GuestbookStickerDto;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

/**
 * 방명록 공개 API (PM 2026-10-10). /api/guestbook/** 는 SecurityConfig permitAll — 로그인 없이 읽고 남긴다.
 * 로그인 상태면 JWT 필터가 인증을 세워 두므로 '회원 이름으로' 남기기와 내 글 삭제가 된다. 관리(숨김·스티커)는 /api/admin/guestbook/**.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "방명록 (공개)", description = "학기별 방명록 읽기·남기기·내 글 삭제·스티커 이미지. 관리는 관리자-운영 관리-방명록 관리")
public class GuestbookController {

    private final GuestbookService service;
    private final FileStorageUtil fileStorageUtil;

    @Operation(summary = "방명록 (비로그인 공개)", description = """
            `semester` 를 비우면 이번 학기. 학기 탭(글이 있는 학기 + 이번 학기), 스티커 서랍, 글 목록을 한 번에.
            ```json
            { "currentSemester": "2026-2학기", "semester": "2026-2학기", "semesters": ["2026-2학기", "2026-1학기"],
              "maxLength": 300, "maxStickers": 3,
              "stickers": [ { "stickerId": 1, "name": "만년필", "imageUrl": "/api/guestbook/stickers/1/image", "sortOrder": 0 } ],
              "notes": [ { "noteId": 12, "displayName": "지나가던 필사인", "isMember": false, "isMine": false,
                  "content": "...", "ink": "ink", "paper": "cream", "tilt": -2,
                  "stickers": [ { "stickerId": 1, "slot": 0, "name": "만년필", "imageUrl": "/api/guestbook/stickers/1/image" } ],
                  "createdAt": "2026-10-10T21:00:00" } ] }
            ```
            ink: ink(검정 잉크)/gray(연한 잉크)/pencil(연필), paper: plain/cream/lined, tilt: 카드 기울기(도)""")
    @GetMapping("/api/guestbook")
    public ResponseEntity<GuestbookResponse> get(@RequestParam(required = false) String semester) {
        return ResponseEntity.ok(service.getGuestbook(semester));
    }

    @Operation(summary = "방명록 남기기 (비로그인 가능)", description = """
            본문 `{ "displayName": "닉네임", "asMember": false, "content": "...", "ink": "ink", "paper": "plain", "stickerIds": [1, 3] }`.
            로그인 상태에서 asMember=true 면 displayName 대신 회원 이름으로 남고 user_id 가 붙는다(본인이 지울 수 있다).
            학기는 서버가 오늘 날짜로 정한다. 201 과 함께 저장된 글을 돌려준다.
            실패: 400(빈 내용·길이 초과·스티커 수 초과), 429(같은 IP/회원이 guestbook_cooldown_seconds 안에 또 남김)""")
    @PostMapping("/api/guestbook")
    public ResponseEntity<GuestbookNoteDto> write(@RequestBody GuestbookNoteRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.write(request, clientIp(http)));
    }

    @Operation(summary = "내가 남긴 글 삭제 (로그인)", description = "회원 이름으로 남긴 내 글만 (소프트). 남의 글·닉네임 글은 403")
    @DeleteMapping("/api/guestbook/{noteId}")
    public ResponseEntity<Map<String, String>> deleteMine(@PathVariable Long noteId) {
        service.deleteMine(noteId);
        return ResponseEntity.ok(Map.of("message", "지웠어요."));
    }

    @Operation(summary = "스티커 이미지 (비로그인 공개)", description = "`stickers[].imageUrl` 이 가리키는 경로. `<img src>` 에 그대로. 없으면 404")
    @GetMapping("/api/guestbook/stickers/{stickerId}/image")
    public ResponseEntity<Resource> stickerImage(@PathVariable Long stickerId) {
        GuestbookStickerDto s = service.requireSticker(stickerId);
        File file = fileStorageUtil.load(s.getFileUrl());
        if (file == null) throw new BaseException("스티커 이미지가 없어요.", HttpStatus.NOT_FOUND);
        MediaType type = MediaType.IMAGE_PNG;
        try {
            if (s.getFileType() != null) type = MediaType.parseMediaType(s.getFileType());
        } catch (RuntimeException ignored) {
            // 저장된 타입이 이상하면 png 로
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(file.length())
                .header("X-Content-Type-Options", "nosniff")
                // 스티커는 바뀌지 않는다(바꾸려면 새 스티커) — 하루 캐시
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
