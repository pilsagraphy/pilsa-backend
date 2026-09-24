package com.back.admin.post.service;

import com.back.admin.post.dto.AdminPostDetailResponse;
import com.back.admin.post.dto.AdminPostPageResponse;


public interface AdminPostService {

    // 전체 게시글 목록 조회 (최신순, 게시판 필터, 제목+글쓴이 검색, 페이징)
    AdminPostPageResponse getPostList(int page, int size, Long boardId, String keyword);

    // 게시글 상세 (state 무관, 댓글·첨부 포함)
    AdminPostDetailResponse getPostDetail(Long postId);
}
