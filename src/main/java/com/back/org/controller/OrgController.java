package com.back.org.controller;

import com.back.global.exception.BaseException;
import com.back.global.util.FileStorageUtil;
import com.back.org.dto.OrgPresidentRow;
import com.back.org.dto.OrgResponse;
import com.back.org.mapper.OrgMapper;
import com.back.org.service.OrgService;
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

/**
 * 조직 공개 조회 — 소개 페이지 '조직도'(가장 최근 학기)와 '역대 회장'(기수별 사진 · 재임 기간 · 학기별 임원진).
 * 둘 다 비로그인 공개 화면이라 SecurityConfig 에서 /api/org/** 를 permitAll 로 연다. 편집은 /api/admin/org/**.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "조직 (공개)", description = "역대 회장 · 학기별 임원진 · 현재 조직도. 편집은 관리자-조직도 편집(/api/admin/org/**)")
public class OrgController {

    private final OrgService orgService;
    private final OrgMapper orgMapper;
    private final FileStorageUtil fileStorageUtil;

    @Operation(summary = "조직 전체 (비로그인 공개)", description = """
            역대 회장(기수 순)과 각 기수의 학기별 임원진. 소개 페이지 조직도는 `currentTerm`(마지막 기수의 마지막 학기)을 그린다.
            ```json
            { "currentTerm": "2026-2학기",
              "presidents": [ { "presidentId": 5, "seqNo": 5, "order": "5대 회장", "name": "최재연",
                  "startYear": 2026, "endYear": null, "period": "(2026~현재)", "photoUrl": "/images/leader/leader_5.png",
                  "officers": [ { "term": "2026-2학기",
                      "roles": [ { "role": "회장", "names": ["최재연"] }, { "role": "부회장", "names": ["최성현"] } ],
                      "teams": [ { "title": "제작스터디", "leader": "박수민", "members": ["김아란"] } ],
                      "advisors": [] } ] } ] }
            ```
            photoUrl 이 `/api/..` 로 시작하면 서버에 올린 사진(아래 API), `/images/..` 면 프론트 정적 파일이다.""")
    @GetMapping("/api/org")
    public ResponseEntity<OrgResponse> getOrganization() {
        return ResponseEntity.ok(orgService.getOrganization());
    }

    @Operation(summary = "회장 사진 (비로그인 공개)", description = "관리자가 조직도 편집에서 올린 사진. `<img src>` 에 그대로. 없으면 404")
    @GetMapping("/api/org/presidents/{presidentId}/photo")
    public ResponseEntity<Resource> getPhoto(@PathVariable Long presidentId) {
        OrgPresidentRow row = orgMapper.findPresidentById(presidentId);
        File file = row == null || row.getPhotoUrl() == null ? null : fileStorageUtil.load(row.getPhotoUrl());
        if (file == null) {
            throw new BaseException("사진이 없습니다.", HttpStatus.NOT_FOUND);
        }
        MediaType type = MediaType.IMAGE_JPEG;
        try {
            if (row.getPhotoType() != null) type = MediaType.parseMediaType(row.getPhotoType());
        } catch (RuntimeException ignored) {
            // 저장된 타입이 이상하면 jpeg 로 내려준다 — 브라우저가 실제 바이트로 판단한다
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(file.length())
                .header("X-Content-Type-Options", "nosniff")
                // 사진을 바꾸면 photo_url 의 파일명이 달라져 프론트가 ?v= 로 캐시를 깬다 — 한 시간 캐시
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(new FileSystemResource(file));
    }
}
