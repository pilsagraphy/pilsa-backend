package com.back.admin.sanction.service;

import com.back.admin.sanction.dto.BulkResultResponse;
import com.back.admin.sanction.dto.ReportEntryResponse;
import com.back.admin.sanction.dto.ReportPageResponse;

import java.util.List;

/**
 * 신고 관리(관리자).
 *
 * 조치 API는 전부 "선택 처리(일괄)" 하나로 통일한다 — 단건 조치는 targetIds 에 1건만 담아 호출한다.
 * 신고 관리·게시글 관리·댓글 관리 화면이 같은 API를 쓴다.
 */
public interface ReportAdminService {

    // 신고된 게시글 목록 (state=blind/deleted 필터·검색·게시판 필터. state 미지정 시 normal 제외)
    ReportPageResponse getReportedPosts(int page, int size, String state, String keyword, Long boardId, String sort);

    // 신고된 댓글 목록 (state=blind/deleted 필터·검색·게시판 필터. state 미지정 시 normal 제외)
    ReportPageResponse getReportedComments(int page, int size, String state, String keyword, Long boardId, String sort);

    // 대상 1건에 들어온 개별 신고 목록 (신고 처리 모달 '신고자 목록'). created_at 오름차순, 신고자 익명
    List<ReportEntryResponse> getReportsByTarget(String targetType, Long targetId);

    // 선택 복원(=신고 반려): 대상 복원(state=normal) + 신고 rejected. 사유를 받지 않는다
    BulkResultResponse selectRestore(String targetType, List<Long> targetIds);

    // 선택 삭제: 대상 소프트 삭제(state=deleted, 주의 +2) + 신고 resolved
    BulkResultResponse selectDelete(String targetType, List<Long> targetIds, Long reasonId, String detail);

    // 선택 블라인드: 대상 state=blind (벌점 없음, 신고는 pending 유지)
    BulkResultResponse selectBlind(String targetType, List<Long> targetIds, Long reasonId, String detail);
}
