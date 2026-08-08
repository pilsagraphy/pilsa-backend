package com.back.board.controller;

import com.back.board.dto.*;
import com.back.board.service.BoardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 게시판(공지/자유/정보) 통합 컨트롤러.
 * boardId 로 게시판을 구분한다. (1=공지사항, 2=자유게시판, 3=정보게시판)
 * 예) GET /api/stu/2/posts → 자유게시판 전체 조회
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stu/{boardId}")
public class BoardController {

    private final BoardService boardService;

    // 카테고리 불러오기 (공지사항은 빈 목록)
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(@PathVariable Long boardId) {
        log.info("게시판 카테고리 목록 조회 요청 - boardId: {}", boardId);
        List<CategoryResponse> responses = boardService.getCategoryList(boardId);
        return ResponseEntity.ok(responses);
    }

    // 신규(상단) 5개 조회
    @GetMapping("/top5")
    public ResponseEntity<List<BoardTop5Response>> getTop5Posts(@PathVariable Long boardId) {
        log.info("메인 화면용 상단 5개 조회 요청 - boardId: {}", boardId);
        List<BoardTop5Response> responses = boardService.getTop5Posts(boardId);
        return ResponseEntity.ok(responses);
    }

    // 전체 조회
    @GetMapping("/posts")
    public ResponseEntity<BoardPageResponse> getAllPosts(
            @PathVariable Long boardId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", defaultValue = "created") String sort) {
        log.info("게시글 목록 조회 요청 - boardId: {}, page: {}, size: {}, categoryId: {}, keyword: {}, sort: {}",
                boardId, page, size, categoryId, keyword, sort);
        BoardPageResponse response = boardService.getPostList(boardId, page, size, categoryId, keyword, sort);
        log.info("게시글 조회 성공 - 현재 페이지 데이터 개수: {}", response.getPosts().size());
        return ResponseEntity.ok(response);
    }

    // 단일글 조회
    @GetMapping("/posts/{postId}")
    public ResponseEntity<BoardDetailResponse> getPostDetail(
            @PathVariable Long boardId,
            @PathVariable Long postId,
            @RequestParam(required = false, defaultValue = "created") String sort) {
        log.info("게시글 상세 조회 요청 - boardId: {}, postId: {}, sort: {}", boardId, postId, sort);
        BoardDetailResponse response = boardService.getPostDetail(boardId, postId, sort);
        return ResponseEntity.ok(response);
    }

    // 좋아요 토글
    @PatchMapping("/posts/{postId}/like")
    public ResponseEntity<BoardResponse> toggleLike(@PathVariable Long boardId, @PathVariable Long postId) {
        log.info("게시글 좋아요 토글 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.togglePostLike(boardId, postId));
    }

    // 게시글 등록
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BoardResponse> createPost(@PathVariable Long boardId, @ModelAttribute BoardRequest request) {
        log.info("게시글 등록 요청 - boardId: {}, title: {}", boardId, request.getTitle());
        return ResponseEntity.ok(boardService.createPost(boardId, request));
    }

    // 게시글 수정
    @PutMapping("/posts/{postId}")
    public ResponseEntity<BoardResponse> updatePost(
            @PathVariable Long boardId,
            @PathVariable Long postId,
            @RequestBody BoardUpdateRequest request) {
        log.info("게시글 수정 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.updatePost(boardId, postId, request));
    }

    // 게시글 삭제
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<BoardResponse> deletePost(@PathVariable Long boardId, @PathVariable Long postId) {
        log.info("게시글 삭제 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.deletePost(boardId, postId));
    }

    // 댓글 등록
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long boardId,
            @PathVariable Long postId,
            @RequestBody CommentRequest request) {
        log.info("댓글 등록 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.createComment(boardId, postId, request));
    }

    // 댓글 수정
    @PutMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long boardId,
            @PathVariable Long commentId,
            @RequestBody CommentRequest request) {
        log.info("댓글 수정 요청 - boardId: {}, commentId: {}", boardId, commentId);
        return ResponseEntity.ok(boardService.updateComment(boardId, commentId, request));
    }

    // 댓글 삭제
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<BoardResponse> deleteComment(
            @PathVariable Long boardId,
            @PathVariable Long commentId) {
        log.info("댓글 삭제 요청 - boardId: {}, commentId: {}", boardId, commentId);
        return ResponseEntity.ok(boardService.deleteComment(boardId, commentId));
    }
}
