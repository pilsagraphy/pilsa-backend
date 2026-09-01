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

    // 상단 N개 조회 (num = 프론트가 요청한 개수)
    List<BoardTopPostResponse> getTopPosts(Long boardId, int num);

    // 전체 조회
    BoardPageResponse getPostList(Long boardId, int page, int size, Long categoryId, String keyword, String sort);

    // 단일글 상세 조회 (댓글 미포함 — 댓글은 getComments 로 따로 조회)
    BoardDetailResponse getPostDetail(Long boardId, Long postId);

    // 게시글의 댓글 목록 (블라인드·삭제 댓글 제외, 익명/비밀댓글 마스킹 적용)
    List<CommentDetailResponse> getComments(Long boardId, Long postId);

    // 좋아요 토글
    BoardResponse togglePostLike(Long boardId, Long postId);

    // 파일 선업로드(본문 이미지·첨부 공용)는 AttachmentService(POST .../files)가 담당한다 — 여기 두지 않는다

    // 게시글 등록, 수정, 삭제 (등록 응답에는 postId 포함 — 상세 이동용)
    BoardResponse createPost(Long boardId, BoardRequest request);
    BoardResponse updatePost(Long boardId, Long postId, BoardUpdateRequest request);
    BoardResponse deletePost(Long boardId, Long postId);

    // 댓글 등록, 수정, 삭제
    CommentResponse createComment(Long boardId, Long postId, CommentRequest request);
    CommentResponse updateComment(Long boardId, Long commentId, CommentRequest request);
    BoardResponse deleteComment(Long boardId, Long commentId);
}
