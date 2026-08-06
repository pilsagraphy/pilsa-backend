package com.back.admin.post.controller;

import com.back.admin.common.dto.BulkResultResponse;
import com.back.admin.post.dto.AdminPostDetailResponse;
import com.back.admin.post.dto.AdminPostPageResponse;
import com.back.admin.post.dto.AdminPostResponse;
import com.back.admin.post.dto.BulkPostDeleteRequest;
import com.back.admin.post.dto.PostModerationRequest;
import com.back.admin.post.service.AdminPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    // 전체 게시글 목록 (최신순, 게시판 필터, 제목+글쓴이 검색, 페이징)
    @GetMapping("/api/admin/posts")
    public ResponseEntity<AdminPostPageResponse> getPosts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        log.info("[관리자] 게시글 목록 조회 - page:{}, size:{}, boardId:{}, keyword:{}", page, size, boardId, keyword);
        return ResponseEntity.ok(adminPostService.getPostList(page, size, boardId, keyword));
    }

    // 게시글 상세 (블라인드/삭제 글도 열람, 댓글·첨부 포함)
    @GetMapping("/api/admin/posts/{postId}")
    public ResponseEntity<AdminPostDetailResponse> getPostDetail(@PathVariable Long postId) {
        log.info("[관리자] 게시글 상세 조회 - postId:{}", postId);
        return ResponseEntity.ok(adminPostService.getPostDetail(postId));
    }

    // 블라인드
    @PatchMapping("/api/admin/posts/{postId}/blind")
    public ResponseEntity<AdminPostResponse> blindPost(
            @PathVariable Long postId,
            @RequestBody PostModerationRequest request) {
        log.info("[관리자] 게시글 블라인드 - postId:{}, reasonId:{}", postId, request.getReasonId());
        adminPostService.blindPost(postId, request.getReasonId(), request.getDetail());
        return ResponseEntity.ok(new AdminPostResponse("블라인드 처리되었습니다."));
    }

    // 공개(복원)
    @PatchMapping("/api/admin/posts/{postId}/restore")
    public ResponseEntity<AdminPostResponse> restorePost(@PathVariable Long postId) {
        log.info("[관리자] 게시글 공개(복원) - postId:{}", postId);
        adminPostService.restorePost(postId);
        return ResponseEntity.ok(new AdminPostResponse("공개 상태로 복원되었습니다."));
    }

    // 소프트 삭제
    @DeleteMapping("/api/admin/posts/{postId}")
    public ResponseEntity<AdminPostResponse> deletePost(
            @PathVariable Long postId,
            @RequestBody PostModerationRequest request) {
        log.info("[관리자] 게시글 삭제 - postId:{}, reasonId:{}", postId, request.getReasonId());
        adminPostService.deletePost(postId, request.getReasonId(), request.getDetail());
        return ResponseEntity.ok(new AdminPostResponse("삭제되었습니다."));
    }

    // 선택 삭제 (일괄, 부분 성공) — 성공 개수 + 실패 항목(id, 사유) 반환
    @PostMapping("/api/admin/posts/bulk-delete")
    public ResponseEntity<BulkResultResponse> bulkDeletePosts(@RequestBody BulkPostDeleteRequest request) {
        log.info("[관리자] 게시글 선택 삭제 - count:{}", request.getPostIds() == null ? 0 : request.getPostIds().size());
        BulkResultResponse result = adminPostService.bulkDeletePosts(request.getPostIds(), request.getReasonId(), request.getDetail());
        return ResponseEntity.ok(result);
    }
}
