package com.back.report.service;

import com.back.admin.common.dto.BulkResultResponse;
import com.back.report.dto.ReportPageResponse;

import java.util.List;

/**
 * 신고 관리(관리자).
 *
 * 조치 API는 전부 "선택 처리(일괄)" 하나로 통일한다 — 단건 조치는 targetIds 에 1건만 담아 호출한다.
 * 신고 관리·게시글 관리·댓글 관리 화면이 같은 API를 쓴다.
 */
public interface ReportAdminService {

    // 신고된 게시글 목록
    ReportPageResponse getReportedPosts(int page, int size, String status, Long boardId, String sort);

    // 신고된 댓글 목록
    ReportPageResponse getReportedComments(int page, int size, String status, Long boardId, String sort);

    // 선택 복원(=신고 반려): 대상 복원(state=normal) + 신고 rejected. 사유를 받지 않는다
    BulkResultResponse selectRestore(String targetType, List<Long> targetIds);

    // 선택 삭제: 대상 소프트 삭제(state=deleted, 주의 +2) + 신고 resolved
    BulkResultResponse selectDelete(String targetType, List<Long> targetIds, Long reasonId, String detail);

    // 선택 블라인드: 대상 state=blind (벌점 없음, 신고는 pending 유지)
    BulkResultResponse selectBlind(String targetType, List<Long> targetIds, Long reasonId, String detail);
}
