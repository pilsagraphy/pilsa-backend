package com.back.board.report.mapper;

import com.back.board.report.dto.ReportReasonResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 신고 "접수"(+사유 목록) 매퍼. 신고 처리(복원/삭제/블라인드) 조회·조치는 admin.sanction 의 ReportAdminMapper 가 담당한다.
@Mapper
public interface ReportMapper {

    // 신고 사유 카테고리 목록 (reasons 테이블, 노출 순서 오름차순)
    List<ReportReasonResponse> findReasons();

    // 활성 사유의 code (없거나 비활성이면 null) — 사유 존재 검증 + detail 정책(ETC 일 때만) 판정에 쓴다
    String findActiveReasonCode(@Param("reasonId") Long reasonId);

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
