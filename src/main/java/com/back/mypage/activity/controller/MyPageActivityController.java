package com.back.mypage.activity.controller;

import com.back.mypage.activity.dto.MyCommentPageResponse;
import com.back.mypage.activity.dto.MyPostPageResponse;
import com.back.mypage.activity.service.MyPageActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지-활동목록",
        description = "마이페이지 상단 통계 클릭 시 나오는 목록 — 내가 쓴 글 / 내가 쓴 댓글(대댓글 포함) / 좋아요 누른 글. " +
                "블라인드·삭제(state!='normal')는 제외. 졸업 후에도 전부 노출(재학/졸업 시점 컬럼 없음).")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/mypage")
public class MyPageActivityController {

    private final MyPageActivityService myPageActivityService;

    @Operation(summary = "내가 쓴 글",
            description = "필터: boardId(선택)/keyword(선택), 정렬 sort(created|viewCount, 기본 created), 페이징. " +
                    "응답 posts[]: postId/boardId/boardName/title/likeCount/viewCount/created + totalPages/totalCount.")
    @GetMapping("/posts")
    public ResponseEntity<MyPostPageResponse> getMyPosts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", defaultValue = "created") String sort) {
        return ResponseEntity.ok(myPageActivityService.getMyPosts(page, size, boardId, keyword, sort));
    }

    @Operation(summary = "내가 쓴 댓글 (대댓글 포함)",
            description = "원글 제목 조인. 필터: boardId(원글 기준)/keyword(댓글·원글제목), 최신순, 페이징. " +
                    "응답 comments[]: commentId/postId/boardId/postTitle/content/created + totalPages/totalCount.")
    @GetMapping("/comments")
    public ResponseEntity<MyCommentPageResponse> getMyComments(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(myPageActivityService.getMyComments(page, size, boardId, keyword));
    }

    @Operation(summary = "좋아요 누른 글",
            description = "필터: boardId(선택)/keyword(선택), 정렬 sort(created=최근 좋아요순|viewCount), 페이징. " +
                    "응답 posts[]: postId/boardId/boardName/title/likeCount/viewCount/created + totalPages/totalCount.")
    @GetMapping("/likes")
    public ResponseEntity<MyPostPageResponse> getMyLikedPosts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", defaultValue = "created") String sort) {
        return ResponseEntity.ok(myPageActivityService.getMyLikedPosts(page, size, boardId, keyword, sort));
    }
}
