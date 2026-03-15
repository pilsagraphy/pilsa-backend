package com.back.student.info.service;

import com.back.student.info.dto.*;
import java.util.List;

public interface InfoService {
    // 카테고리 목록 조회
    List<CategoryResponse> getCategoryList();

    // 상단 5개 조회
    List<InfoTop5Response> getTop5Posts();

    // 전체 조회
    InfoPageResponse getPostList(int page, int size, Long categoryId, String keyword, String sort);

    // 단일글 상세 조회
    InfoDetailResponse getPostDetail(Long postId, String sort);

    // 좋아요 토글
    InfoResponse togglePostLike(Long postId);

    // 게시글 등록, 수정, 삭제
    InfoResponse createPost(InfoRequest request);
    InfoResponse updatePost(Long postId, InfoUpdateRequest request);
    InfoResponse deletePost(Long postId);

    // 댓글 등록, 수정, 삭제
    CommentResponse createComment(Long postId, CommentRequest request);
    CommentResponse updateComment(Long commentId, CommentRequest request);
    InfoResponse deleteComment(Long commentId);
}