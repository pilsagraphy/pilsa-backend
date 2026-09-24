package com.back.admin.comment.mapper;

import com.back.admin.comment.dto.AdminCommentListResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminCommentMapper {

    // 전체 댓글 목록 (모든 게시판 통합, 최신순, 게시판 필터, 내용+글쓴이 검색, 페이징)
    // deleted 상태는 제외한다.
    List<AdminCommentListResponse> findComments(
            @Param("boardId") Long boardId,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    // 위 조건에 해당하는 총 개수 (페이징 계산용)
    int countComments(
            @Param("boardId") Long boardId,
            @Param("keyword") String keyword
    );
}
