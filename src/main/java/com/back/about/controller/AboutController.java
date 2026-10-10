package com.back.about.controller;

import com.back.about.dto.HistoryYearDto;
import com.back.about.dto.IntroSectionDto;
import com.back.about.service.AboutService;
import com.back.global.exception.BaseException;
import com.back.global.util.FileStorageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;

/** 동아리 소개 · 연혁 공개 조회 (비로그인). 편집은 /api/admin/about/** */
@RestController
@RequiredArgsConstructor
@Tag(name = "소개 · 연혁 (공개)", description = "동아리 소개 문단과 연도별 연혁. 편집은 관리자-운영 관리-소개/연혁 관리")
public class AboutController {

    private final AboutService service;
    private final FileStorageUtil fileStorageUtil;

    @Operation(summary = "동아리 소개 문단 (비로그인 공개)", description = "`[{ sectionId, title, content, sortOrder }]` 순서대로. content 는 줄바꿈 포함")
    @GetMapping("/api/intro")
    public ResponseEntity<List<IntroSectionDto>> intro() {
        return ResponseEntity.ok(service.getIntro());
    }

    @Operation(summary = "연혁 (비로그인 공개)", description = """
            연도 오름차순. `[{ year, activities: [{ itemId, text, href, video, linkHref, linkLabel, images: [{ src, alt, wide, zoom }] }] }]`
            href 가 있으면 글 자체가 바로가기, video 는 mp4 경로, link* 는 영상 아래 바로가기, images 는 16:9 상자에 가로로""")
    @GetMapping("/api/history")
    public ResponseEntity<List<HistoryYearDto>> history() {
        return ResponseEntity.ok(service.getHistory());
    }

    @Operation(summary = "연혁 사진 파일 (비로그인 공개)", description = "관리자가 올린 사진. images[].src 가 /api/history/images/{name} 이면 여기")
    @GetMapping("/api/history/images/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new BaseException("사진이 없어요.", HttpStatus.NOT_FOUND);
        }
        File file = fileStorageUtil.load("/uploads/history/" + name);
        if (file == null) throw new BaseException("사진이 없어요.", HttpStatus.NOT_FOUND);
        String lower = name.toLowerCase();
        MediaType type = lower.endsWith(".png") ? MediaType.IMAGE_PNG : lower.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(file.length())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(new FileSystemResource(file));
    }
}
