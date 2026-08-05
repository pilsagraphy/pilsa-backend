package com.back.admin.report.service;

import com.back.admin.common.dto.BulkResultResponse;
import com.back.admin.report.dto.ReportPageResponse;

import java.util.List;

public interface AdminReportService {

    // 신고된 게시글 목록
    ReportPageResponse getReportedPosts(int page, int size, String status, Long boardId, String sort);

    // 신고된 댓글 목록
    ReportPageResponse getReportedComments(int page, int size, String status, Long boardId, String sort);

    // 반려: 대상 복원(state=normal) + 신고 rejected 처리
    void reject(String targetType, Long targetId);

    // 삭제: 대상 소프트 삭제(state=deleted, 주의 +2) + 신고 resolved 처리
    void delete(String targetType, Long targetId);

    // 선택 반려 (일괄, 부분 성공)
    BulkResultResponse bulkReject(String targetType, List<Long> targetIds);

    // 선택 삭제 (일괄, 부분 성공)
    BulkResultResponse bulkDelete(String targetType, List<Long> targetIds);
}
