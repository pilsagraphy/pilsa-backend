package com.back.admin.post.controller;

import com.back.admin.post.dto.AdminPostDetailResponse;
import com.back.admin.post.dto.AdminPostPageResponse;
import com.back.admin.post.service.AdminPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 게시글 관리(관리자) — 조회 전용.
 *
 * 블라인드/복원/삭제 조치는 신고 관리의 선택 처리 API(/api/admin/reports/select-*)를 함께 쓴다.
 * 단건이면 targetIds 에 1건만 담아 호출한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-게시글 관리",
        description = "게시글 관리 페이지 — 조회 전용. 전 게시판의 게시글을 검색·열람한다(블라인드 글 포함, 익명글 실작성자 노출). 블라인드/복원/삭제 조치는 신고 관리의 선택 처리 API(/api/admin/reports/select-blind, select-restore, select-delete)를 사용한다(단건이면 targetIds에 1건만 담아 호출).")
public class AdminPostController {

    private final AdminPostService adminPostService;

    // 전체 게시글 목록 (최신순, 게시판 필터, 제목+글쓴이 검색, 페이징)
    @Operation(summary = "게시글 목록 (관리자, 전 게시판)",
            description = """
                    게시글 관리 페이지 진입·검색 시 호출한다. 전 게시판의 게시글을 최신순으로 내려주며,
                    게시판 필터(boardId)와 제목+글쓴이 검색(keyword)을 지원한다. 조회 전용 API로, 조치는 /api/admin/reports/select-* 를 사용한다.

                    ### 요청 예시
                    ```
                    GET /api/admin/posts?page=1&size=10&boardId=2&keyword=제목또는글쓴이
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "totalPages":5,"totalCount":48,
                      "posts":[{"postId":171,"boardId":2,"boardName":"자유게시판","title":"제목",
                        "authorName":"홍길동","commentCount":4,"likeCount":2,"viewCount":15,
                        "created":"2026-08-14T10:12:30","state":"normal"}]
                    }
                    ```

                    state: normal | blind (deleted 는 목록에서 제외).
                    """)
    @GetMapping("/api/admin/posts")
    public ResponseEntity<AdminPostPageResponse> getPosts(
            @Parameter(description = "페이지 번호 (1부터)", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지당 게시글 수", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "게시판 필터 (미지정 시 전 게시판)", example = "2")
            @RequestParam(value = "boardId", required = false) Long boardId,
            @Parameter(description = "제목+글쓴이 검색어", example = "홍길동")
            @RequestParam(value = "keyword", required = false) String keyword) {
        log.info("[관리자] 게시글 목록 조회 - page:{}, size:{}, boardId:{}, keyword:{}", page, size, boardId, keyword);
        return ResponseEntity.ok(adminPostService.getPostList(page, size, boardId, keyword));
    }

    // 게시글 상세 (블라인드/삭제 글도 열람, 댓글·첨부 포함)
    @Operation(summary = "게시글 상세 (관리자)",
            description = """
                    게시글 관리 페이지에서 게시글을 클릭해 상세를 볼 때 호출한다. 블라인드/삭제(blind/deleted) 글도 열람 가능하며,
                    익명글도 실작성자를 노출하고 모든 state 의 댓글을 포함한다. 관리자 열람이므로 조회수는 증가하지 않는다. 조회 전용 API.

                    ### 요청 예시
                    ```
                    GET /api/admin/posts/171
                    ```
                    (본문 없음)

                    ### 응답 예시
                    ```json
                    {
                      "postId":171,"boardId":2,"boardName":"자유게시판","categoryName":"일상",
                      "title":"제목","content":"본문","authorId":85,"authorName":"홍길동",
                      "isAnonymous":false,"isPinned":false,"viewCount":15,"likeCount":2,
                      "commentCount":4,"state":"blind",
                      "created":"2026-08-14T10:12:30","updated":"2026-08-14T11:00:00",
                      "attachments":[{"attachmentId":18,"originName":"파일1.png","fileSize":12345,"fileUrl":"/api/user/files/18"}],
                      "comments":[{"commentId":200,"content":"댓글","userId":84,"authorName":"관리자",
                        "isAnonymous":false,"isPrivate":false,"state":"normal",
                        "created":"2026-08-14T10:30:00","updated":null}]
                    }
                    ```
                    """)
    @GetMapping("/api/admin/posts/{postId}")
    public ResponseEntity<AdminPostDetailResponse> getPostDetail(
            @Parameter(description = "조회할 게시글 id", example = "171") @PathVariable Long postId) {
        log.info("[관리자] 게시글 상세 조회 - postId:{}", postId);
        return ResponseEntity.ok(adminPostService.getPostDetail(postId));
    }
}
