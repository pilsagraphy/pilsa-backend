package com.back.report.controller;

import com.back.report.dto.ReportRequest;
import com.back.report.dto.ReportResponse;
import com.back.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // 게시글/댓글 신고 접수
    @PostMapping("/api/stu/reports")
    public ResponseEntity<ReportResponse> submitReport(@RequestBody ReportRequest request) {
        log.info("신고 접수 요청 - targetType: {}, targetId: {}", request.getTargetType(), request.getTargetId());
        reportService.submitReport(request);
        return ResponseEntity.ok(new ReportResponse("신고가 접수되었습니다."));
    }
}
