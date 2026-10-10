package com.back.gallery.controller;

import com.back.gallery.dto.GalleryPhotoDto;
import com.back.gallery.dto.GalleryResponse;
import com.back.gallery.mapper.GalleryMapper;
import com.back.gallery.service.GalleryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 활동 사진 (PM 2026-10-10 밤). 보기는 비로그인 공개(/api/gallery/**, permitAll), 올리기·지우기는 회원(/api/user/gallery/**).
 * 관리(숨김 목록·복원)는 /api/admin/gallery/**.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "활동 사진", description = "학기별 사진 보기(공개) · 회원 업로드/삭제 · 사진 파일")
public class GalleryController {

    private final GalleryService service;
    private final GalleryMapper mapper;
    private final FileStorageUtil fileStorageUtil;

    @Operation(summary = "활동 사진 (비로그인 공개)", description = """
            `semester` 를 비우면 이번 학기. 학기 목록(사진이 있는 학기 + 이번 학기)과 그 학기 사진.
            ```json
            { "currentSemester": "2026-2학기", "semester": "2026-2학기", "semesters": ["2026-2학기", "2026-1학기"], "maxFilesPerUpload": 10,
              "photos": [ { "photoId": 31, "semesterLabel": "2026-2학기", "userId": 8, "uploaderName": "박수민",
                  "imageUrl": "/api/gallery/photos/31/image", "ratio": 1.5, "title": "정기모임", "hashtags": ["#정기모임"],
                  "isMine": false, "canManage": false, "state": "normal", "createdAt": "2026-10-10T22:00:00" } ] }
            ```
            imageUrl 이 /images/.. 면 프론트 정적 파일(시드), /api/.. 면 서버 사진. 관리자에게는 숨긴 사진(state=hidden)도 내려간다""")
    @GetMapping("/api/gallery")
    public ResponseEntity<GalleryResponse> get(@RequestParam(required = false) String semester) {
        return ResponseEntity.ok(service.getGallery(semester));
    }

    @Operation(summary = "사진 파일 (비로그인 공개)", description = "`photos[].imageUrl` 이 가리키는 경로. 없으면 404")
    @GetMapping("/api/gallery/photos/{photoId}/image")
    public ResponseEntity<Resource> image(@PathVariable Long photoId) {
        GalleryPhotoDto p = mapper.findById(photoId);
        File file = p == null || p.getFileUrl() == null ? null : fileStorageUtil.load(p.getFileUrl());
        if (file == null) throw new BaseException("사진이 없어요.", HttpStatus.NOT_FOUND);
        MediaType type = MediaType.IMAGE_JPEG;
        try {
            if (p.getFileType() != null) type = MediaType.parseMediaType(p.getFileType());
        } catch (RuntimeException ignored) {
            // 저장된 타입이 이상하면 jpeg 로
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(file.length())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(new FileSystemResource(file));
    }

    @Operation(summary = "사진 올리기 (로그인 회원)", description = """
            multipart `files`(여러 장, 이미지만, 한 번에 gallery_max_files_per_upload 장), `ratios`(파일 순서대로 가로÷세로, 선택),
            `title`(선택), `hashtags`(선택, 쉼표/공백 구분). 학기는 서버가 오늘 날짜로 매긴다. 201 과 올린 사진들""")
    @PostMapping("/api/user/gallery")
    public ResponseEntity<List<GalleryPhotoDto>> upload(@RequestPart("files") List<MultipartFile> files,
                                                        @RequestParam(value = "ratios", required = false) List<Double> ratios,
                                                        @RequestParam(value = "title", required = false) String title,
                                                        @RequestParam(value = "hashtags", required = false) String hashtags) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(files, ratios, title, hashtags));
    }

    @Operation(summary = "사진 지우기 (로그인)", description = "내 사진은 deleted(소프트). 관리자가 남의 사진을 지우면 hidden(복원 가능). 그 외 403")
    @DeleteMapping("/api/user/gallery/{photoId}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long photoId) {
        service.delete(photoId);
        return ResponseEntity.ok(Map.of("message", "지웠어요."));
    }
}
