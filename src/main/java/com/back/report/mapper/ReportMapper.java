package com.back.report.mapper;

import com.back.report.dto.ReportDto;
import com.back.report.dto.ReportedContentResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper {

    // 신고 접수
    void insertReport(@Param("reporterId") Long reporterId,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId,
                       @Param("reasonId") Long reasonId,
                       @Param("detail") String detail);

    // 신고 단건 조회 (수락/거절 처리 전 상태 확인용)
    ReportDto findReportById(@Param("reportId") Long reportId);

    // 게시글/댓글은 board 도메인에 상관없이 posts/comments 테이블을 직접 참조 (신고 대상은 자유/정보게시판 어디든 될 수 있음)
    Long findPostAuthorId(@Param("postId") Long postId);
    Long findCommentAuthorId(@Param("commentId") Long commentId);
    int softDeletePost(@Param("postId") Long postId);
    int softDeleteComment(@Param("commentId") Long commentId);

    // 신고 수락(삭제 처리) - moderation_log 액션과 연결
    void resolveReport(@Param("reportId") Long reportId, @Param("resolutionActionId") Long resolutionActionId);

    // 신고 거절
    void rejectReport(@Param("reportId") Long reportId);

    // 특정 회원이 작성한 게시글/댓글이 받은 신고 내역 전체
    List<ReportedContentResponse> findReportsByTargetAuthor(@Param("userId") Long userId);

    // 신고가 수락(삭제 처리)된 건수 - 제재회원 현황 화면용
    int countResolvedDeletionsByUser(@Param("userId") Long userId);
}
