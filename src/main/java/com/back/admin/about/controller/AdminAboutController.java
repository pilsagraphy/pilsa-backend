package com.back.admin.about.controller;

import com.back.about.dto.HistoryItemDto;
import com.back.about.dto.IntroSectionDto;
import com.back.about.service.AboutService;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 운영 관리 > 동아리 소개 관리 · 연혁 관리 (PM 2026-10-11). 관리자(Lv1+). 전부 소프트삭제 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/about")
@Tag(name = "관리자-운영 관리-소개/연혁 관리", description = "동아리 소개 문단 CRUD · 연혁 항목 CRUD · 연혁 사진 업로드")
public class AdminAboutController {

    private final AboutService service;

    @Operation(summary = "소개 문단 목록", description = "공개 GET /api/intro 와 같다 (관리 화면 편의)")
    @GetMapping("/intro")
    public ResponseEntity<List<IntroSectionDto>> intro() {
        AuthUtils.requireAdmin();
        return ResponseEntity.ok(service.getIntro());
    }

    @Operation(summary = "소개 문단 추가", description = "본문 `{ title, content, sortOrder? }`. 201")
    @PostMapping("/intro")
    public ResponseEntity<IntroSectionDto> createIntro(@RequestBody IntroSectionDto body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createIntro(body));
    }

    @Operation(summary = "소개 문단 수정", description = "본문 `{ title, content, sortOrder }`. 없으면 404")
    @PutMapping("/intro/{sectionId}")
    public ResponseEntity<IntroSectionDto> updateIntro(@PathVariable Long sectionId, @RequestBody IntroSectionDto body) {
        return ResponseEntity.ok(service.updateIntro(sectionId, body));
    }

    @Operation(summary = "소개 문단 삭제 (소프트)")
    @DeleteMapping("/intro/{sectionId}")
    public ResponseEntity<Map<String, String>> deleteIntro(@PathVariable Long sectionId) {
        service.deleteIntro(sectionId);
        return ResponseEntity.ok(Map.of("message", "문단을 삭제했습니다."));
    }

    @Operation(summary = "연혁 항목 전부 (평평한 목록)", description = "연도·순서순 `[{ itemId, year, sortOrder, text, href, video, linkHref, linkLabel, images[] }]`")
    @GetMapping("/history")
    public ResponseEntity<List<HistoryItemDto>> history() {
        return ResponseEntity.ok(service.getHistoryItems());
    }

    @Operation(summary = "연혁 항목 추가", description = "본문 `{ year, text, href?, video?, linkHref?, linkLabel?, images?: [{ src, alt, wide, zoom }], sortOrder? }` — 순서를 비우면 그 해 맨 뒤. 201")
    @PostMapping("/history")
    public ResponseEntity<HistoryItemDto> createHistory(@RequestBody HistoryItemDto body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createHistory(body));
    }

    @Operation(summary = "연혁 항목 수정", description = "본문은 추가와 같다. 없으면 404")
    @PutMapping("/history/{itemId}")
    public ResponseEntity<HistoryItemDto> updateHistory(@PathVariable Long itemId, @RequestBody HistoryItemDto body) {
        return ResponseEntity.ok(service.updateHistory(itemId, body));
    }

    @Operation(summary = "연혁 항목 삭제 (소프트)")
    @DeleteMapping("/history/{itemId}")
    public ResponseEntity<Map<String, String>> deleteHistory(@PathVariable Long itemId) {
        service.deleteHistory(itemId);
        return ResponseEntity.ok(Map.of("message", "항목을 삭제했습니다."));
    }

    @Operation(summary = "연혁 사진 올리기", description = "multipart `file`(이미지). `{ src }` 를 돌려주니 항목의 images[].src 에 넣는다 (/api/history/images/{name})")
    @PostMapping("/history/images")
    public ResponseEntity<Map<String, String>> uploadHistoryImage(@RequestPart("file") MultipartFile file) {
        AuthUtils.requireAdmin();
        String type = file == null ? null : file.getContentType();
        if (file == null || file.isEmpty() || type == null || !type.startsWith("image/") || type.contains("svg")) {
            throw new BaseException("이미지 파일만 올릴 수 있어요.", HttpStatus.BAD_REQUEST);
        }
        // 원본 파일명을 쓰면 공백·#·% 가 든 이름이 <img src> 에서 깨진다 — UUID 이름으로 직접 쓴다 (방명록 그림과 같은 방식)
        String ext = type.contains("png") ? ".png" : type.contains("webp") ? ".webp" : type.contains("gif") ? ".gif" : ".jpg";
        String name = java.util.UUID.randomUUID().toString().replace("-", "") + ext;
        java.io.File dir = new java.io.File(new java.io.File("").getAbsolutePath(), "uploads/history");
        if (!dir.exists() && !dir.mkdirs()) throw new BaseException("사진을 저장하지 못했어요.", HttpStatus.INTERNAL_SERVER_ERROR);
        try {
            file.transferTo(new java.io.File(dir, name));
        } catch (java.io.IOException e) {
            throw new BaseException("사진을 저장하지 못했어요.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.ok(Map.of("src", "/api/history/images/" + name));
    }
}
