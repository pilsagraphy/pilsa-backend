package com.back.board.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 신고 "접수" 매퍼. 신고 처리(반려/삭제) 조회는 같은 패키지의 ReportAdminMapper 가 담당한다.
@Mapper
public interface ReportMapper {

    // 신고 접수
    void insertReport(@Param("reporterId") Long reporterId,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId,
                       @Param("reasonId") Long reasonId,
                       @Param("detail") String detail);

    // 신고 대상 존재/작성자 확인 — 게시판 도메인에 상관없이 posts/comments 를 직접 참조
    Long findPostAuthorId(@Param("postId") Long postId);
    Long findCommentAuthorId(@Param("commentId") Long commentId);

    // 신고 대상의 현재 표시 상태 (normal/blind/deleted) — 이미 삭제된 대상 접수 차단용
    String findPostState(@Param("postId") Long postId);
    String findCommentState(@Param("commentId") Long commentId);
}
