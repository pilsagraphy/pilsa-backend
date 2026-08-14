package com.back.board.service;

import com.back.board.dto.*;

import java.util.List;

/**
 * 게시판(공지/자유/정보) 통합 서비스.
 * 모든 메서드는 boardId(1=공지, 2=자유, 3=정보)를 받아 게시판을 구분한다.
 */
public interface BoardService {

    // 카테고리 목록 조회 (공지사항은 빈 목록)
    List<CategoryResponse> getCategoryList(Long boardId);

    // 상단 5개 조회
    List<BoardTop5Response> getTop5Posts(Long boardId);

    // 전체 조회
    BoardPageResponse getPostList(Long boardId, int page, int size, Long categoryId, String keyword, String sort);

    // 단일글 상세 조회
    BoardDetailResponse getPostDetail(Long boardId, Long postId, String sort);

    // 좋아요 토글
    BoardResponse togglePostLike(Long boardId, Long postId);

    // 게시글 등록, 수정, 삭제
    BoardResponse createPost(Long boardId, BoardRequest request);
    BoardDetailResponse updatePost(Long boardId, Long postId, BoardUpdateRequest request);
    BoardResponse deletePost(Long boardId, Long postId);

    // 댓글 등록, 수정, 삭제
    CommentResponse createComment(Long boardId, Long postId, CommentRequest request);
    CommentResponse updateComment(Long boardId, Long commentId, CommentRequest request);
    BoardResponse deleteComment(Long boardId, Long commentId);
}
