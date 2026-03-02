package com.back.student.free.controller;

import com.back.student.free.dto.*;
import com.back.student.free.service.FreeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FreeController {

    private final FreeService freeService;

    // 자게 카테고리 불러오기
    @GetMapping("/api/stu/free/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        log.info("자유게시판 카테고리 목록 조회 요청");
        List<CategoryResponse> responses = freeService.getCategoryList();
        return ResponseEntity.ok(responses);
    }

    // 자게 신규 5개 조회
    @GetMapping("/api/stu/free/top5")
    public ResponseEntity<List<FreeTop5Response>> getTop5Posts() {
        log.info("메인 화면용 자유게시판 상단 5개 조회 요청");
        List<FreeTop5Response> responses = freeService.getTop5Posts();
        return ResponseEntity.ok(responses);
    }

    // 자게 전체 조회
    @GetMapping("/api/stu/free/posts")
    public ResponseEntity<FreePageResponse> getAllPosts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", defaultValue = "created") String sort) {
        log.info("자게 목록 조회 요청 시작 - page: {}, size: {}, categoryId: {}, keyword: {}, sort: {}", page, size, categoryId, keyword, sort);
        FreePageResponse response = freeService.getPostList(page, size, categoryId, keyword, sort);
        log.info("자게 조회 성공 - 현재 페이지 데이터 개수: {}", response.getPosts().size());
        return ResponseEntity.ok(response);
    }

    // 자게 단일글 조회
    @GetMapping("/api/stu/free/posts/{postId}")
    public ResponseEntity<FreeDetailResponse> getPostDetail(
            @PathVariable Long postId,
            @RequestParam(required = false, defaultValue = "created") String sort) {
        log.info("자게 상세 조회 요청 - ID: {}, sort: {}", postId, sort);
        FreeDetailResponse response = freeService.getPostDetail(postId, sort);
        return ResponseEntity.ok(response);
    }

    // 자게 좋아요
    @PatchMapping("/api/stu/boards/free/posts/{postId}/like")
    public ResponseEntity<FreeResponse> toggleLike(@PathVariable Long postId) {
        log.info("자게 좋아요 토글 요청 - 게시글 ID: {}", postId);
        return ResponseEntity.ok(freeService.togglePostLike(postId));
    }

    // 자유게시글 등록
    @PostMapping(value = "/api/stu/free/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FreeResponse> createPost(@ModelAttribute FreeRequest request) {
        log.info("자유게시글 등록 요청 데이터: {}", request.getTitle());
        return ResponseEntity.ok(freeService.createPost(request));
    }

    // 자유게시글 수정
    @PutMapping("/api/stu/free/posts/{postId}")
    public ResponseEntity<FreeResponse> updatePost(
            @PathVariable Long postId,
            @RequestBody FreeUpdateRequest request) {
        log.info("자유게시글 수정 요청 - ID: {}", postId);
        return ResponseEntity.ok(freeService.updatePost(postId, request));
    }

    // 자유게시글 삭제
    @DeleteMapping("/api/stu/free/posts/{postId}")
    public ResponseEntity<FreeResponse> deletePost(@PathVariable Long postId) {
        log.info("자유게시글 삭제 요청 - ID: {}", postId);
        return ResponseEntity.ok(freeService.deletePost(postId));
    }

    // 자게 댓글 등록
    @PostMapping("/api/stu/free/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId,
            @RequestBody CommentRequest request) {
        log.info("자게 댓글 등록 요청 - 게시글 ID: {}", postId);
        return ResponseEntity.ok(freeService.createComment(postId, request));
    }

    // 자게 댓글 수정
    @PutMapping("/api/stu/free/posts/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long commentId,
            @RequestBody CommentRequest request) {
        log.info("자게 댓글 수정 요청 - 댓글 ID: {}", commentId);
        return ResponseEntity.ok(freeService.updateComment(commentId, request));
    }

    // 자게 댓글 삭제
    @DeleteMapping("/api/stu/free/posts/{postId}/comments/{commentId}")
    public ResponseEntity<FreeResponse> deleteComment(@PathVariable Long commentId) {
        log.info("자게 댓글 삭제 요청 - 댓글 ID: {}", commentId);
        return ResponseEntity.ok(freeService.deleteComment(commentId));
    }
}