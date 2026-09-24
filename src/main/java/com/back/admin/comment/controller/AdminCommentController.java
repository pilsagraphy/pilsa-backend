package com.back.admin.comment.controller;

import com.back.admin.comment.dto.AdminCommentPageResponse;
import com.back.admin.comment.service.AdminCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 댓글 관리(관리자) — 조회 전용.
 *
 * 게시글 관리(AdminPostController)와 동일한 패턴이다.
 * 블라인드/복원/삭제 조치는 신고 관리의 선택 처리 API(/api/admin/reports/select-*)를 함께 쓴다
 * (targetType="comment" 로 호출, 단건이면 targetIds 에 1건만 담는다).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-댓글 관리",
        description = "댓글 관리 페이지 — 조회 전용. 전 게시판의 댓글을 검색·열람한다(블라인드 댓글 포함, 익명 댓글 실작성자 노출). "
                + "각 행의 원글(postId) 링크로 원글 상세로 이동할 수 있다. 블라인드/복원/삭제 조치는 신고 관리의 선택 처리 API"
                + "(/api/admin/reports/select-blind, select-restore, select-delete)를 targetType=\"comment\" 로 호출한다(단건이면 targetIds에 1건만 담아 호출).")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    // 전체 댓글 목록 (최신순, 게시판 필터, 내용+글쓴이 검색, 페이징)
    @Operation(summary = "댓글 목록 (관리자, 전 게시판)",
            description = """
                    댓글 관리 페이지 진입·검색 시 호출한다. 전 게시판의 댓글을 최신순으로 내려주며,
                    게시판 필터(boardId)와 내용+글쓴이 검색(keyword)을 지원한다. 조회 전용 API로, 조치는 /api/admin/reports/select-* 를 사용한다.
                    각 행에는 원글 상세로 이동하기 위한 postId(원글 링크)가 포함된다.

                    ### 요청 예시
                    ```
                    GET /api/admin/comments?page=1&size=10&boardId=2&keyword=내용또는글쓴이
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "totalPages":3,"totalCount":24,
                      "comments":[{"commentId":200,"postId":171,"boardId":2,"boardName":"자유게시판",
                        "authorName":"홍길동","content":"댓글 내용",
                        "created":"2026-08-14T10:30:00","state":"normal"}]
                    }
                    ```

                    state: normal | blind (deleted 는 목록에서 제외).
                    postId = 원글 상세 이동용 게시글 id (밑줄 링크).
                    """)
    @GetMapping("/api/admin/comments")
    public ResponseEntity<AdminCommentPageResponse> getComments(
            @Parameter(description = "페이지 번호 (1부터)", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지당 댓글 수", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "게시판 필터 (미지정 시 전 게시판)", example = "2")
            @RequestParam(value = "boardId", required = false) Long boardId,
            @Parameter(description = "내용+글쓴이 검색어", example = "홍길동")
            @RequestParam(value = "keyword", required = false) String keyword) {
        log.info("[관리자] 댓글 목록 조회 - page:{}, size:{}, boardId:{}, keyword:{}", page, size, boardId, keyword);
        return ResponseEntity.ok(adminCommentService.getCommentList(page, size, boardId, keyword));
    }
}
