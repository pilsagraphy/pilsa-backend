package com.back.admin.report.controller;

import com.back.admin.common.dto.BulkResultResponse;
import com.back.admin.report.dto.AdminReportResponse;
import com.back.admin.report.dto.ReportBulkRequest;
import com.back.admin.report.dto.ReportPageResponse;
import com.back.admin.report.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    // 게시글 신고 목록 (status: pending/rejected/resolved, 프론트 표기 블라인드/반려/삭제)
    @GetMapping("/api/admin/reports/posts")
    public ResponseEntity<ReportPageResponse> getReportedPosts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "sort", defaultValue = "latest") String sort) {
        log.info("[관리자] 게시글 신고 목록 - status:{}, boardId:{}, sort:{}", status, boardId, sort);
        return ResponseEntity.ok(adminReportService.getReportedPosts(page, size, status, boardId, sort));
    }

    // 댓글 신고 목록
    @GetMapping("/api/admin/reports/comments")
    public ResponseEntity<ReportPageResponse> getReportedComments(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "sort", defaultValue = "latest") String sort) {
        log.info("[관리자] 댓글 신고 목록 - status:{}, boardId:{}, sort:{}", status, boardId, sort);
        return ResponseEntity.ok(adminReportService.getReportedComments(page, size, status, boardId, sort));
    }

    // 반려 (단건)
    @PatchMapping("/api/admin/reports/{targetType}/{targetId}/reject")
    public ResponseEntity<AdminReportResponse> reject(
            @PathVariable String targetType,
            @PathVariable Long targetId) {
        log.info("[관리자] 신고 반려 - {}:{}", targetType, targetId);
        adminReportService.reject(targetType, targetId);
        return ResponseEntity.ok(new AdminReportResponse("반려 처리되었습니다."));
    }

    // 삭제 (단건)
    @DeleteMapping("/api/admin/reports/{targetType}/{targetId}")
    public ResponseEntity<AdminReportResponse> delete(
            @PathVariable String targetType,
            @PathVariable Long targetId) {
        log.info("[관리자] 신고 삭제 - {}:{}", targetType, targetId);
        adminReportService.delete(targetType, targetId);
        return ResponseEntity.ok(new AdminReportResponse("삭제되었습니다."));
    }

    // 선택 반려 (일괄, 부분 성공)
    @PostMapping("/api/admin/reports/bulk-reject")
    public ResponseEntity<BulkResultResponse> bulkReject(@RequestBody ReportBulkRequest request) {
        log.info("[관리자] 신고 선택 반려 - type:{}, count:{}", request.getTargetType(),
                request.getTargetIds() == null ? 0 : request.getTargetIds().size());
        BulkResultResponse result = adminReportService.bulkReject(request.getTargetType(), request.getTargetIds());
        return ResponseEntity.ok(result);
    }

    // 선택 삭제 (일괄, 부분 성공)
    @PostMapping("/api/admin/reports/bulk-delete")
    public ResponseEntity<BulkResultResponse> bulkDelete(@RequestBody ReportBulkRequest request) {
        log.info("[관리자] 신고 선택 삭제 - type:{}, count:{}", request.getTargetType(),
                request.getTargetIds() == null ? 0 : request.getTargetIds().size());
        BulkResultResponse result = adminReportService.bulkDelete(request.getTargetType(), request.getTargetIds());
        return ResponseEntity.ok(result);
    }
}
