package com.back.admin.sanction.mapper;

import com.back.admin.sanction.dto.ReportedItemResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportAdminMapper {

    // 신고된 게시글 목록 (대상별 그룹핑, 상태(state)/게시판/검색 필터, 정렬, 페이징)
    // state: blind/deleted. 미지정 시 normal 제외(블라인드+삭제만) — 복구(복원)된 대상은 목록에서 빠진다.
    List<ReportedItemResponse> findReportedPosts(
            @Param("state") String state,
            @Param("keyword") String keyword,
            @Param("boardId") Long boardId,
            @Param("sort") String sort,
            @Param("offset") int offset,
            @Param("size") int size
    );

    int countReportedPosts(
            @Param("state") String state,
            @Param("keyword") String keyword,
            @Param("boardId") Long boardId
    );

    // 신고된 댓글 목록 (상태(state)/게시판/검색 필터). 미지정 시 normal 제외
    List<ReportedItemResponse> findReportedComments(
            @Param("state") String state,
            @Param("keyword") String keyword,
            @Param("boardId") Long boardId,
            @Param("sort") String sort,
            @Param("offset") int offset,
            @Param("size") int size
    );

    int countReportedComments(
            @Param("state") String state,
            @Param("keyword") String keyword,
            @Param("boardId") Long boardId
    );

    // 대상의 대표(최신) 신고 사유 reason_id — 삭제 시 moderation_log 에 기록
    Long findLatestReasonId(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId
    );

    // 대상의 pending 신고를 일괄 상태변경 (rejected / resolved). active_flag 는 생성컬럼이라 자동 갱신
    // resolutionActionId: 수락(삭제)로 종료된 경우 연결할 moderation_log 액션 (반려는 null)
    int updatePendingReportsStatus(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("status") String status,
            @Param("resolutionActionId") Long resolutionActionId
    );
}
