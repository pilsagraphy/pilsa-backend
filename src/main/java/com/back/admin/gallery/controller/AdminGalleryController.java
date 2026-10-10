package com.back.admin.gallery.controller;

import com.back.gallery.dto.GalleryPhotoDto;
import com.back.gallery.mapper.GalleryMapper;
import com.back.gallery.service.GalleryService;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 운영 관리 > 활동 사진 관리 (PM 2026-10-10 밤). 관리자(Lv1+). 숨김(=DELETE /api/user/gallery/{id} 가 관리자면 hidden)·복원.
 * 로직이 짧아 서비스 계층 없이 매퍼를 바로 쓴다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/gallery")
@Tag(name = "관리자-운영 관리-활동 사진 관리", description = "학기별 사진 전부(숨김 포함) · 숨김/복원. 공개 조회는 GET /api/gallery")
public class AdminGalleryController {

    private final GalleryMapper mapper;
    private final GalleryService service;

    @Operation(summary = "학기별 사진 전부 (숨긴 사진 포함)", description = "`semester` 비우면 이번 학기. `{ semester, semesters, photos: [ { ..., state } ] }`")
    @GetMapping("/photos")
    public ResponseEntity<Map<String, Object>> photos(@RequestParam(required = false) String semester) {
        AuthUtils.requireAdmin();
        String current = service.currentSemesterLabel();
        List<String> semesters = new ArrayList<>(mapper.findSemesters(true));
        if (!semesters.contains(current)) semesters.add(0, current);
        String picked = semester == null || semester.isBlank() ? current : semester;
        List<GalleryPhotoDto> photos = mapper.findPhotos(picked, true);
        service.decorate(photos);
        return ResponseEntity.ok(Map.of("semester", picked, "semesters", semesters, "photos", photos));
    }

    @Operation(summary = "사진 숨기기", description = "normal → hidden. 이미 숨겼거나 지운 사진이면 404")
    @PatchMapping("/photos/{photoId}/hide")
    @Transactional
    public ResponseEntity<Map<String, String>> hide(@PathVariable Long photoId) {
        AuthUtils.requireAdmin();
        if (mapper.updateState(photoId, "normal", "hidden") == 0) throw new BaseException("숨길 사진이 없어요.", HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(Map.of("message", "사진을 숨겼습니다."));
    }

    @Operation(summary = "사진 복원", description = "hidden → normal")
    @PatchMapping("/photos/{photoId}/restore")
    public ResponseEntity<GalleryPhotoDto> restore(@PathVariable Long photoId) {
        return ResponseEntity.ok(service.restore(photoId));
    }

    @Operation(summary = "제목·해시태그 수정", description = "본문 `{ \"title\": \"정기모임\", \"hashtags\": \"정기모임, 가을\" }` (해시태그는 쉼표/공백 구분, # 은 없어도 된다). 바뀐 사진을 돌려준다")
    @PutMapping("/photos/{photoId}")
    public ResponseEntity<GalleryPhotoDto> updateMeta(@PathVariable Long photoId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.updateMeta(photoId, body.get("title"), body.get("hashtags")));
    }
}
