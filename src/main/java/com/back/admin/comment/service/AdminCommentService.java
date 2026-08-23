package com.back.admin.comment.service;

import com.back.admin.comment.dto.AdminCommentPageResponse;

public interface AdminCommentService {

    // 전체 댓글 목록 조회 (최신순, 게시판 필터, 내용+글쓴이 검색, 페이징)
    AdminCommentPageResponse getCommentList(int page, int size, Long boardId, String keyword);
}
