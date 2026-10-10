package com.back.admin.guestbook.controller;

import com.back.admin.guestbook.mapper.AdminGuestbookMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import com.back.guestbook.dto.GuestbookNoteDto;
import com.back.guestbook.dto.GuestbookStickerDto;
import com.back.guestbook.mapper.GuestbookMapper;
import com.back.guestbook.service.GuestbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 운영 관리 > 방명록 관리 (PM 2026-10-10). 관리자(Lv1+). 글은 숨김/복원만(소프트), 스티커는 등록·이름/순서·삭제(소프트).
 * 로직이 짧아 서비스 계층 없이 컨트롤러가 매퍼를 바로 쓴다 (일정 템플릿과 같은 이유).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/guestbook")
@Tag(name = "관리자-운영 관리-방명록 관리", description = "글 숨김/복원 · 스티커 등록/수정/삭제. 공개 조회는 GET /api/guestbook")
public class AdminGuestbookController {

    private final AdminGuestbookMapper mapper;
    private final GuestbookMapper guestbookMapper;
    private final GuestbookService guestbookService;
    private final FileStorageUtil fileStorageUtil;

    @Operation(summary = "학기별 글 전부 (숨긴 글·지운 글 포함)", description = "`semester` 비우면 이번 학기. `{ semester, semesters, notes: [ { ..., state: normal|hidden|deleted } ] }`")
    @GetMapping("/notes")
    public ResponseEntity<Map<String, Object>> notes(@RequestParam(required = false) String semester) {
        AuthUtils.requireAdmin();
        String current = guestbookService.currentSemesterLabel();
        List<String> semesters = new ArrayList<>(mapper.findSemestersAllStates());
        if (!semesters.contains(current)) semesters.add(0, current);
        String picked = semester == null || semester.isBlank() ? current : semester;
        List<GuestbookNoteDto> notes = mapper.findNotesAllStates(picked);
        guestbookService.attachStickers(notes);
        return ResponseEntity.ok(Map.of("semester", picked, "semesters", semesters, "notes", notes));
    }

    @Operation(summary = "글 숨기기", description = "normal → hidden. 방명록 화면에서 사라진다. 이미 숨겼거나 지운 글이면 404")
    @PatchMapping("/notes/{noteId}/hide")
    @Transactional
    public ResponseEntity<Map<String, String>> hide(@PathVariable Long noteId) {
        AuthUtils.requireAdmin();
        if (mapper.updateNoteState(noteId, "normal", "hidden") == 0) throw new BaseException("숨길 글이 없어요.", HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(Map.of("message", "글을 숨겼습니다."));
    }

    @Operation(summary = "글 복원", description = "hidden → normal. 작성자가 지운 글(deleted)은 되돌리지 않는다")
    @PatchMapping("/notes/{noteId}/restore")
    @Transactional
    public ResponseEntity<Map<String, String>> restore(@PathVariable Long noteId) {
        AuthUtils.requireAdmin();
        if (mapper.updateNoteState(noteId, "hidden", "normal") == 0) throw new BaseException("복원할 글이 없어요.", HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(Map.of("message", "글을 복원했습니다."));
    }

    @Operation(summary = "스티커 목록", description = "서랍에 보이는 스티커 전부, 순서대로")
    @GetMapping("/stickers")
    public ResponseEntity<List<GuestbookStickerDto>> stickers() {
        AuthUtils.requireAdmin();
        return ResponseEntity.ok(guestbookMapper.findStickers());
    }

    @Operation(summary = "스티커 등록", description = "multipart `file`(이미지, 배경 투명 PNG 권장) + `name`. 맨 뒤 순서로 들어간다. 201")
    @PostMapping("/stickers")
    @Transactional
    public ResponseEntity<GuestbookStickerDto> createSticker(@RequestPart("file") MultipartFile file, @RequestParam String name) {
        AuthUtils.requireAdmin();
        String type = file == null ? null : file.getContentType();
        if (file == null || file.isEmpty() || type == null || !type.startsWith("image/") || type.contains("svg")) {
            throw new BaseException("이미지 파일만 올릴 수 있어요.", HttpStatus.BAD_REQUEST);
        }
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 50) throw new BaseException("스티커 이름은 1~50자예요.", HttpStatus.BAD_REQUEST);
        Integer max = mapper.findMaxStickerOrder();
        GuestbookStickerDto s = new GuestbookStickerDto();
        s.setName(n);
        s.setFileUrl(fileStorageUtil.save(file, "uploads/guestbook/stickers"));
        s.setFileType(type);
        s.setSortOrder(max == null ? 0 : max + 1);
        mapper.insertSticker(s, AuthUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(guestbookMapper.findStickerById(s.getStickerId()));
    }

    @Operation(summary = "스티커 이름·순서 수정", description = "본문 `{ \"name\": \"..\", \"sortOrder\": 2 }`. 없으면 404")
    @PutMapping("/stickers/{stickerId}")
    @Transactional
    public ResponseEntity<GuestbookStickerDto> updateSticker(@PathVariable Long stickerId, @RequestBody GuestbookStickerDto body) {
        AuthUtils.requireAdmin();
        String n = body.getName() == null ? "" : body.getName().strip();
        if (n.isEmpty() || n.length() > 50) throw new BaseException("스티커 이름은 1~50자예요.", HttpStatus.BAD_REQUEST);
        int order = body.getSortOrder() == null ? 0 : body.getSortOrder();
        if (mapper.updateSticker(stickerId, n, order) == 0) throw new BaseException("스티커가 없어요.", HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(guestbookMapper.findStickerById(stickerId));
    }

    @Operation(summary = "스티커 삭제 (소프트)", description = "서랍에서 빠진다. 이미 붙은 글의 스티커는 그대로 보인다 — 파일도 남긴다")
    @DeleteMapping("/stickers/{stickerId}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteSticker(@PathVariable Long stickerId) {
        AuthUtils.requireAdmin();
        if (mapper.softDeleteSticker(stickerId) == 0) throw new BaseException("스티커가 없어요.", HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(Map.of("message", "스티커를 삭제했습니다."));
    }
}
