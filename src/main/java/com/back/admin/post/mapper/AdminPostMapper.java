package com.back.admin.post.mapper;

import com.back.admin.post.dto.AdminAttachmentResponse;
import com.back.admin.post.dto.AdminCommentResponse;
import com.back.admin.post.dto.AdminPostDetailResponse;
import com.back.admin.post.dto.AdminPostListResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminPostMapper {

    // 전체 게시글 목록 (모든 게시판 통합, 최신순, 게시판 필터, 제목+글쓴이 검색, 페이징)
    // deleted 상태는 제외한다.
    List<AdminPostListResponse> findPosts(
            @Param("boardId") Long boardId,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    // 위 조건에 해당하는 총 개수 (페이징 계산용)
    int countPosts(
            @Param("boardId") Long boardId,
            @Param("keyword") String keyword
    );

    // 게시글 상세 (state 필터 없음 → 블라인드/삭제 글도 조회). 없으면 null
    AdminPostDetailResponse findPostDetail(@Param("postId") Long postId);

    // 게시글의 첨부파일 목록
    List<AdminAttachmentResponse> findAttachments(@Param("postId") Long postId);

    // 게시글의 댓글 목록 (모든 state 포함)
    List<AdminCommentResponse> findComments(@Param("postId") Long postId);
}
