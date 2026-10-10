package com.back.admin.event.controller;

import com.back.admin.event.dto.EventTemplateDto;
import com.back.admin.event.mapper.AdminEventTemplateMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 일정 템플릿 — 관리자 일정 등록 폼의 '템플릿' (정기모임·제작스터디·MT 처럼 반복되는 내용). PM 2026-10-10.
 * 제목·구분·세부 사항·시각만 담고 날짜는 담지 않는다. 등록·수정·삭제 전부 관리자(Lv1+).
 * 로직이 검증 두 줄뿐이라 서비스 계층 없이 컨트롤러가 매퍼를 바로 쓴다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/event/templates")
@Tag(name = "관리자-일정 템플릿")
public class AdminEventTemplateController {

    private final AdminEventTemplateMapper mapper;

    @Operation(summary = "템플릿 목록", description = "이름순. `[{ templateId, name, title, category, description, startTime, endTime }]` — startTime 이 null 이면 종일")
    @GetMapping
    public ResponseEntity<List<EventTemplateDto>> list() {
        AuthUtils.requireAdmin();
        return ResponseEntity.ok(mapper.findAll());
    }

    @Operation(summary = "템플릿 등록", description = "본문 `{ name, title, category, description, startTime, endTime }`. name·title 필수. 만든 행을 돌려준다")
    @PostMapping
    @Transactional
    public ResponseEntity<EventTemplateDto> create(@RequestBody EventTemplateDto request) {
        AuthUtils.requireAdmin();
        validate(request);
        mapper.insert(request, AuthUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.findById(request.getTemplateId()));
    }

    @Operation(summary = "템플릿 수정", description = "본문은 등록과 같다. 없거나 지워진 템플릿은 404")
    @PutMapping("/{templateId}")
    @Transactional
    public ResponseEntity<EventTemplateDto> update(@PathVariable Long templateId, @RequestBody EventTemplateDto request) {
        AuthUtils.requireAdmin();
        validate(request);
        if (mapper.update(templateId, request) == 0) {
            throw new BaseException("템플릿을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(mapper.findById(templateId));
    }

    @Operation(summary = "템플릿 삭제 (소프트)", description = "`{ \"message\": \"템플릿을 삭제했습니다.\" }`")
    @DeleteMapping("/{templateId}")
    @Transactional
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long templateId) {
        AuthUtils.requireAdmin();
        if (mapper.softDelete(templateId) == 0) {
            throw new BaseException("템플릿을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Map.of("message", "템플릿을 삭제했습니다."));
    }

    private static void validate(EventTemplateDto request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BaseException("템플릿 이름을 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BaseException("일정 제목을 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        request.setName(request.getName().trim());
        request.setTitle(request.getTitle().trim());
        // 'HH:mm' 만 받는다 — 빈 값은 종일
        request.setStartTime(normalizeTime(request.getStartTime()));
        request.setEndTime(normalizeTime(request.getEndTime()));
    }

    private static String normalizeTime(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("^\\d{2}:\\d{2}$")) {
            throw new BaseException("시각은 HH:mm 형식이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }
}
