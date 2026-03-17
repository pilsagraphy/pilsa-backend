package com.back.student.free.service;

import com.back.student.free.dto.*;
import java.util.List;

public interface FreeService {
    // 카테고리 목록 조회
    List<CategoryResponse> getCategoryList();

    // 상단 5개 조회
    List<FreeTop5Response> getTop5Posts();

    // 전체 조회
    FreePageResponse getPostList(int page, int size, Long categoryId, String keyword, String sort);

    // 단일글 상세 조회
    FreeDetailResponse getPostDetail(Long postId, String sort);

    // 좋아요 토글
    FreeResponse togglePostLike(Long postId);

    // 게시글 등록, 수정, 삭제
    FreeResponse createPost(FreeRequest request);
    FreeResponse updatePost(Long postId, FreeUpdateRequest request);
    FreeResponse deletePost(Long postId);

    // 댓글 등록, 수정, 삭제
    CommentResponse createComment(Long postId, CommentRequest request);
    CommentResponse updateComment(Long commentId, CommentRequest request);
    FreeResponse deleteComment(Long commentId);
}