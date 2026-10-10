package com.back.admin.guestbook.controller;

import com.back.admin.guestbook.mapper.AdminGuestbookMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.guestbook.dto.GuestbookNoteDto;
import com.back.guestbook.service.GuestbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 운영 관리 > 방명록 관리 (PM 2026-10-10). 관리자(Lv1+). 글은 숨김/복원만(소프트).
 * 로직이 짧아 서비스 계층 없이 컨트롤러가 매퍼를 바로 쓴다 (일정 템플릿과 같은 이유).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/guestbook")
@Tag(name = "관리자-운영 관리-방명록 관리", description = "글 숨김/복원. 공개 조회는 GET /api/guestbook")
public class AdminGuestbookController {

    private final AdminGuestbookMapper mapper;
    private final GuestbookService guestbookService;

    @Operation(summary = "학기별 글 전부 (숨긴 글·지운 글 포함)", description = "`semester` 비우면 이번 학기. `{ semester, semesters, notes: [ { ..., state: normal|hidden|deleted } ] }`")
    @GetMapping("/notes")
    public ResponseEntity<Map<String, Object>> notes(@RequestParam(required = false) String semester) {
        AuthUtils.requireAdmin();
        String current = guestbookService.currentSemesterLabel();
        List<String> semesters = new ArrayList<>(mapper.findSemestersAllStates());
        if (!semesters.contains(current)) semesters.add(0, current);
        String picked = semester == null || semester.isBlank() ? current : semester;
        List<GuestbookNoteDto> notes = mapper.findNotesAllStates(picked);
        guestbookService.decorate(notes);
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
}
