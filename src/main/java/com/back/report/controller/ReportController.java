package com.back.report.controller;

import com.back.report.dto.ReportRequest;
import com.back.report.dto.ReportResponse;
import com.back.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "신고 접수", description = "로그인 회원이면 신분(재학생/졸업생)·관리자 여부와 무관하게 동일하게 사용")
public class ReportController {

    private final ReportService reportService;

    // 게시글/댓글 신고 접수
    // 경로에 stu/alu 구분을 두지 않는다 — 신고는 모든 회원 공통 기능이다
    @Operation(summary = "게시글/댓글 신고 접수",
            description = "동일 대상 중복 신고는 409, 본인 글은 400, 이미 삭제된 대상은 409를 반환합니다.")
    @PostMapping("/api/reports")
    public ResponseEntity<ReportResponse> submitReport(@RequestBody ReportRequest request) {
        log.info("신고 접수 요청 - targetType: {}, targetId: {}", request.getTargetType(), request.getTargetId());
        reportService.submitReport(request);
        return ResponseEntity.ok(new ReportResponse("신고가 접수되었습니다."));
    }
}
