package com.back.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 신고 "접수" 전용 매퍼.
// 신고 처리(반려/삭제)는 admin.report, 회원별 신고 내역 조회는 admin.sanction 이 담당한다. (PM 피드백)
@Mapper
public interface ReportMapper {

    // 신고 접수
    void insertReport(@Param("reporterId") Long reporterId,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId,
                       @Param("reasonId") Long reasonId,
                       @Param("detail") String detail);

    // 신고 대상 존재 확인용 - 게시판 도메인에 상관없이 posts/comments 테이블을 직접 참조
    Long findPostAuthorId(@Param("postId") Long postId);
    Long findCommentAuthorId(@Param("commentId") Long commentId);
}
