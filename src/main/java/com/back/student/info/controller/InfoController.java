package com.back.student.info.controller;

import com.back.student.info.dto.*;
import com.back.student.info.service.InfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InfoController {

    private final InfoService infoService;

    // 정게 카테고리 불러오기
    @GetMapping("/api/stu/info/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        log.info("정보게시판 카테고리 목록 조회 요청");
        List<CategoryResponse> responses = infoService.getCategoryList();
        return ResponseEntity.ok(responses);
    }

    // 정게 신규 5개 조회
    @GetMapping("/api/stu/info/top5")
    public ResponseEntity<List<InfoTop5Response>> getTop5Posts() {
        log.info("메인 화면용 정보게시판 상단 5개 조회 요청");
        List<InfoTop5Response> responses = infoService.getTop5Posts();
        return ResponseEntity.ok(responses);
    }

    // 정게 전체 조회
    @GetMapping("/api/stu/info/posts")
    public ResponseEntity<InfoPageResponse> getAllPosts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", defaultValue = "created") String sort) {
        log.info("정게 목록 조회 요청 시작 - page: {}, size: {}, categoryId: {}, keyword: {}, sort: {}", page, size, categoryId, keyword, sort);
        InfoPageResponse response = infoService.getPostList(page, size, categoryId, keyword, sort);
        log.info("정게 조회 성공 - 현재 페이지 데이터 개수: {}", response.getPosts().size());
        return ResponseEntity.ok(response);
    }

    // 정게 단일글 조회
    @GetMapping("/api/stu/info/posts/{postId}")
    public ResponseEntity<InfoDetailResponse> getPostDetail(
            @PathVariable Long postId,
            @RequestParam(required = false, defaultValue = "created") String sort) {
        log.info("정게 상세 조회 요청 - ID: {}, sort: {}", postId, sort);
        InfoDetailResponse response = infoService.getPostDetail(postId, sort);
        return ResponseEntity.ok(response);
    }

    // 정게 좋아요
    @PatchMapping("/api/stu/info/posts/{postId}/like")
    public ResponseEntity<InfoResponse> toggleLike(@PathVariable Long postId) {
        log.info("정게 좋아요 토글 요청 - 게시글 ID: {}", postId);
        return ResponseEntity.ok(infoService.togglePostLike(postId));
    }

    // 정보게시글 등록
    @PostMapping(value = "/api/stu/info/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InfoResponse> createPost(@ModelAttribute InfoRequest request) {
        log.info("정보게시글 등록 요청 데이터: {}", request.getTitle());
        return ResponseEntity.ok(infoService.createPost(request));
    }

    // 정보게시글 수정
    @PutMapping("/api/stu/info/posts/{postId}")
    public ResponseEntity<InfoResponse> updatePost(
            @PathVariable Long postId,
            @RequestBody InfoUpdateRequest request) {
        log.info("정보게시글 수정 요청 - ID: {}", postId);
        return ResponseEntity.ok(infoService.updatePost(postId, request));
    }

    // 정보게시글 삭제
    @DeleteMapping("/api/stu/info/posts/{postId}")
    public ResponseEntity<InfoResponse> deletePost(@PathVariable Long postId) {
        log.info("정보게시글 삭제 요청 - ID: {}", postId);
        return ResponseEntity.ok(infoService.deletePost(postId));
    }

    // 정게 댓글 등록
    @PostMapping("/api/stu/info/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId,
            @RequestBody CommentRequest request) {
        log.info("정게 댓글 등록 요청 - 게시글 ID: {}, 비밀여부: {}", postId, request.isPrivate());
        return ResponseEntity.ok(infoService.createComment(postId, request));
    }

    // 정게 댓글 수정
    @PutMapping("/api/stu/info/posts/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long commentId,
            @RequestBody CommentRequest request) {
        log.info("정게 댓글 수정 요청 - 댓글 ID: {}", commentId);
        return ResponseEntity.ok(infoService.updateComment(commentId, request));
    }

    // 정게 댓글 삭제
    @DeleteMapping("/api/stu/info/posts/{postId}/comments/{commentId}")
    public ResponseEntity<InfoResponse> deleteComment(@PathVariable Long commentId) {
        log.info("정게 댓글 삭제 요청 - 댓글 ID: {}", commentId);
        return ResponseEntity.ok(infoService.deleteComment(commentId));
    }
}