package com.back.report.controller;

import com.back.admin.common.dto.BulkResultResponse;
import com.back.report.dto.ReportActionResponse;
import com.back.report.dto.ReportBulkRequest;
import com.back.report.dto.ReportPageResponse;
import com.back.report.service.ReportAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportAdminController {

    private final ReportAdminService reportAdminService;

    // 게시글 신고 목록 (status: pending/rejected/resolved, 프론트 표기 블라인드/반려/삭제)
    @GetMapping("/api/admin/reports/posts")
    public ResponseEntity<ReportPageResponse> getReportedPosts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "sort", defaultValue = "latest") String sort) {
        log.info("[관리자] 게시글 신고 목록 - status:{}, boardId:{}, sort:{}", status, boardId, sort);
        return ResponseEntity.ok(reportAdminService.getReportedPosts(page, size, status, boardId, sort));
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
        return ResponseEntity.ok(reportAdminService.getReportedComments(page, size, status, boardId, sort));
    }

    // 반려 (단건)
    @PatchMapping("/api/admin/reports/{targetType}/{targetId}/reject")
    public ResponseEntity<ReportActionResponse> reject(
            @PathVariable String targetType,
            @PathVariable Long targetId) {
        log.info("[관리자] 신고 반려 - {}:{}", targetType, targetId);
        reportAdminService.reject(targetType, targetId);
        return ResponseEntity.ok(new ReportActionResponse("반려 처리되었습니다."));
    }

    // 삭제 (단건)
    @DeleteMapping("/api/admin/reports/{targetType}/{targetId}")
    public ResponseEntity<ReportActionResponse> delete(
            @PathVariable String targetType,
            @PathVariable Long targetId) {
        log.info("[관리자] 신고 삭제 - {}:{}", targetType, targetId);
        reportAdminService.delete(targetType, targetId);
        return ResponseEntity.ok(new ReportActionResponse("삭제되었습니다."));
    }

    // 선택 반려 (일괄, 부분 성공)
    @PostMapping("/api/admin/reports/bulk-reject")
    public ResponseEntity<BulkResultResponse> bulkReject(@RequestBody ReportBulkRequest request) {
        log.info("[관리자] 신고 선택 반려 - type:{}, count:{}", request.getTargetType(),
                request.getTargetIds() == null ? 0 : request.getTargetIds().size());
        BulkResultResponse result = reportAdminService.bulkReject(request.getTargetType(), request.getTargetIds());
        return ResponseEntity.ok(result);
    }

    // 선택 삭제 (일괄, 부분 성공)
    @PostMapping("/api/admin/reports/bulk-delete")
    public ResponseEntity<BulkResultResponse> bulkDelete(@RequestBody ReportBulkRequest request) {
        log.info("[관리자] 신고 선택 삭제 - type:{}, count:{}", request.getTargetType(),
                request.getTargetIds() == null ? 0 : request.getTargetIds().size());
        BulkResultResponse result = reportAdminService.bulkDelete(request.getTargetType(), request.getTargetIds());
        return ResponseEntity.ok(result);
    }
}
