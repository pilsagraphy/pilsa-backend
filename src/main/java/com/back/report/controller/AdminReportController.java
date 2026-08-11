package com.back.report.controller;

import com.back.report.dto.ReportResponse;
import com.back.report.dto.ReportedContentResponse;
import com.back.report.service.ReportAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportAdminService reportAdminService;

    // 특정 회원이 받은 신고 내역 전체 (제재회원 관리 화면3)
    @GetMapping("/api/admin/reports/users/{userId}")
    public ResponseEntity<List<ReportedContentResponse>> getReportsByTargetAuthor(@PathVariable Long userId) {
        log.info("회원별 신고 내역 조회 요청 - userId: {}", userId);
        return ResponseEntity.ok(reportAdminService.getReportsByTargetAuthor(userId));
    }

    // 신고 수락 (삭제 처리 + 작성자 주의 포인트 자동 부여)
    @PostMapping("/api/admin/reports/{reportId}/resolve")
    public ResponseEntity<ReportResponse> resolveReport(@PathVariable Long reportId) {
        Long adminUserId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        log.info("신고 수락 처리 요청 - reportId: {}, 처리 관리자: {}", reportId, adminUserId);
        reportAdminService.resolveReport(reportId, adminUserId);
        return ResponseEntity.ok(new ReportResponse("신고를 수락하여 삭제 처리했습니다."));
    }

    // 신고 거절
    @PostMapping("/api/admin/reports/{reportId}/reject")
    public ResponseEntity<ReportResponse> rejectReport(@PathVariable Long reportId) {
        log.info("신고 거절 처리 요청 - reportId: {}", reportId);
        reportAdminService.rejectReport(reportId);
        return ResponseEntity.ok(new ReportResponse("신고를 거절했습니다."));
    }
}
