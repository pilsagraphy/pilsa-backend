package com.back.board.controller;

import com.back.board.dto.*;
import com.back.board.service.BoardService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 게시판 통합 컨트롤러.
 *
 * 경로에 신분(stu/alu)을 두지 않는다 — 게시판 접근 권한은 boards.read_scope / write_level
 * 데이터로 판정하며, 관리자가 런타임에 만든 게시판도 같은 경로로 동작한다.
 * 예) GET /api/user/boards/2/posts → 자유게시판 전체 조회
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/boards/{boardId}")
@Tag(name = "게시판(글·댓글)", description = "게시판 통합 API. boardId 로 게시판 구분(기본: 1=공지사항, 2=자유게시판, 3=정보게시판). 관리자가 추가한 게시판도 동일 경로 사용")
public class BoardController {

    private final BoardService boardService;

    // boardId 경로변수 공통 설명 (모든 엔드포인트에서 재사용)
    private static final String BOARD_ID_DESC = "게시판 ID (기본 1=공지사항, 2=자유게시판, 3=정보게시판. /api/user/boards 로 목록 조회)";

    @Operation(summary = "카테고리 목록 조회",
            description = """
                    공통게시판 페이지에서 카테고리 필터 탭·글쓰기 폼의 카테고리 선택 UI를 그릴 때 호출합니다.
                    요청 파라미터는 없으며, 관리자에게만 '중요'(code=PINNED) 카테고리가 포함되어 내려갑니다.

                    ### 요청 예시
                    ```
                    GET /api/user/boards/2/categories
                    ```

                    ### 응답 예시
                    ```json
                    [
                      {"categoryId": 4, "name": "일상", "code": "DAILY"},
                      {"categoryId": 5, "name": "질문", "code": "QUESTION"}
                    ]
                    ```
                    ※ code=PINNED 카테고리는 관리자(admin_level≥1)에게만 포함 — 선택 시 상단 고정 글로 등록됩니다.
                    """)
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId) {
        log.info("게시판 카테고리 목록 조회 요청 - boardId: {}", boardId);
        List<CategoryResponse> responses = boardService.getCategoryList(boardId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "상단 N개 조회",
            description = """
                    메인 화면의 게시판 요약 영역에서 상단 글을 프론트가 요청한 개수(num)만큼 조회할 때 호출합니다.
                    중요표시(is_pinned) 글이 우선 정렬되고 그다음 최신순이며, state=normal 글만 내려갑니다.

                    ### 요청 예시
                    ```
                    GET /api/user/boards/1/posts/top/5    → 5건
                    GET /api/user/boards/1/posts/top/3    → 3건
                    ```

                    ### 응답 예시
                    ```json
                    [
                      {"postId": 140, "title": "중요 공지", "isPinned": true},
                      {"postId": 139, "title": "최근 글", "isPinned": false}
                    ]
                    ```

                    실패: 400 {"message":"조회 개수는 1 이상 50 이하여야 합니다."}
                    """)
    @GetMapping("/posts/top/{num}")
    public ResponseEntity<List<BoardTopPostResponse>> getTopPosts(
            @Parameter(description = BOARD_ID_DESC, example = "1") @PathVariable Long boardId,
            @Parameter(description = "가져올 글 개수 (1~50)", example = "5") @PathVariable int num) {
        log.info("메인 화면용 상단 {}개 조회 요청 - boardId: {}", num, boardId);
        List<BoardTopPostResponse> responses = boardService.getTopPosts(boardId, num);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "게시글 전체 조회 (페이징/검색/정렬)",
            description = """
                    공통게시판 페이지 진입·페이지 이동·검색·카테고리 필터 변경 시마다 호출하는 글 목록 API입니다.
                    categoryId(카테고리 필터), keyword(제목·내용 검색), sort(created=최신순, viewCount=조회순)를 지원합니다.

                    ### 요청 예시
                    ```
                    GET /api/user/boards/2/posts?page=1&size=10&categoryId=4&keyword=검색어&sort=created
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "totalPages": 3, "totalCount": 27,
                      "posts": [
                        {"postId": 171, "title": "제목", "authorName": "홍길동", "likeCount": 2,
                         "viewCount": 15, "commentCount": 4, "categoryName": "일상", "isPinned": false,
                         "isAnonymous": false, "hasAttachment": true, "created": "2026-08-14T10:12:30"}
                      ]
                    }
                    ```
                    ※ isAnonymous=true 인 글은 authorName 이 서버에서 "익명"으로 마스킹되어 내려갑니다.
                    ※ 목록에는 created(작성일)만 내려가고, updated(수정일)는 상세 조회에서만 내려갑니다.
                    """)
    @GetMapping("/posts")
    public ResponseEntity<BoardPageResponse> getAllPosts(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "한 페이지당 글 개수", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "카테고리 필터 (선택). 공지사항은 미사용", example = "4")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "검색어 (제목·내용, 선택)", example = "정기모임")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "정렬 기준: created(최신순, 기본값), viewCount(조회순)", example = "created")
            @RequestParam(value = "sort", defaultValue = "created") String sort) {
        log.info("게시글 목록 조회 요청 - boardId: {}, page: {}, size: {}, categoryId: {}, keyword: {}, sort: {}",
                boardId, page, size, categoryId, keyword, sort);
        BoardPageResponse response = boardService.getPostList(boardId, page, size, categoryId, keyword, sort);
        log.info("게시글 조회 성공 - 현재 페이지 데이터 개수: {}", response.getPosts().size());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "게시글 단일 상세 조회",
            description = """
                    목록에서 글 제목을 눌러 상세 페이지로 진입할 때 호출합니다. 조회수가 1 증가하며(자기 글 포함),
                    첨부파일·좋아요·댓글 개수와 이전글/다음글(제목·카테고리·작성일)을 반환합니다.
                    댓글 본문은 내려가지 않습니다 — 별도 API(GET .../posts/{postId}/comments)로 조회합니다.

                    ### 요청 예시
                    ```
                    GET /api/user/boards/2/posts/171?sort=created
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "postId": 171, "boardId": 2, "title": "제목", "content": "본문",
                      "userId": 85, "authorName": "홍길동", "categoryName": "일상",
                      "isAnonymous": false, "isPinned": false,
                      "viewCount": 15, "likeCount": 2, "isLiked": true,
                      "commentCount": 3,
                      "created": "2026-08-14T10:12:30", "updated": "2026-08-14T11:02:11",
                      "prevPost": {"postId": 172, "title": "이전 글 제목", "categoryName": "일상", "created": "2026-08-14T15:40:00"},
                      "nextPost": {"postId": 159, "title": "다음 글 제목", "categoryName": "질문", "created": "2026-08-13T09:20:00"},
                      "attachments": [{"attachmentId": 18, "originName": "파일.pdf", "fileUrl": "uploads/board-2/uuid.pdf", "fileSize": 12345}],
                      "attachmentCount": 1
                    }
                    ```
                    ※ 익명글: authorName="익명", userId=null (관리자·작성자 본인 제외)
                    ※ prevPost/nextPost: 목록과 동일한 순서(중요 우선 + 최신순) 기준으로 현재 글 바로 위(prev)·아래(next) 글.
                       첫 글이면 prevPost=null, 마지막 글이면 nextPost=null. sort=viewCount 면 조회순 기준으로 계산됩니다.
                    ※ 상세에서만 created(작성일)와 updated(수정일)가 함께 내려갑니다.
                    """)
    @GetMapping("/posts/{postId}")
    public ResponseEntity<BoardDetailResponse> getPostDetail(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @Parameter(description = "이전글/다음글 정렬 기준: created(기본값), viewCount", example = "created")
            @RequestParam(required = false, defaultValue = "created") String sort) {
        log.info("게시글 상세 조회 요청 - boardId: {}, postId: {}, sort: {}", boardId, postId, sort);
        BoardDetailResponse response = boardService.getPostDetail(boardId, postId, sort);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "게시글 좋아요 토글",
            description = """
                    상세 페이지의 좋아요 버튼을 누를 때 호출합니다. 이미 눌렀으면 취소, 안 눌렀으면 추가됩니다.

                    ### 요청 예시
                    ```
                    PATCH /api/user/boards/2/posts/171/like    (본문 없음)
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "좋아요 +1"}
                    ```
                    또는
                    ```json
                    {"message": "좋아요 취소"}
                    ```
                    """)
    @PatchMapping("/posts/{postId}/like")
    public ResponseEntity<BoardResponse> toggleLike(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId) {
        log.info("게시글 좋아요 토글 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.togglePostLike(boardId, postId));
    }

    @Operation(summary = "게시글 등록 (파일 업로드)",
            description = """
                    글쓰기 화면에서 등록 버튼을 누를 때 호출합니다(multipart/form-data).
                    content 는 **마크다운 문자열**로 받습니다(HTML 아님) — 에디터가 만든 마크다운을 그대로 전송하세요.
                    응답에 생성된 postId 가 포함되므로 프론트는 그 값으로 상세 페이지로 이동합니다.
                    상단 고정은 요청 필드가 아니라 카테고리 '중요'(code=PINNED) 선택으로 서버가 결정하며(관리자만),
                    isAnonymous(익명)는 익명 허용 게시판에서만 의미가 있습니다.

                    ### 요청 예시
                    ```
                    POST /api/user/boards/2/posts    (multipart/form-data)

                    title: 제목                      ← 필수, 200자 이내
                    content: ## 마크다운 본문         ← 필수, 마크다운 문자열
                    categoryId: 4
                    isAnonymous: false
                    files: 자료.pdf, 사진.png         ← 선택, 다중 첨부
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "게시글이 성공적으로 등록되었습니다.", "postId": 185}
                    ```

                    실패: 400 {"message":"제목은 필수입니다."}
                    실패: 400 {"message":"제목은 200자를 넘을 수 없습니다."}
                    실패: 400 {"message":"내용은 필수입니다."}
                    실패: 403 {"message":"이 게시판에 글을 등록할 권한이 없습니다."}
                    """)
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BoardResponse> createPost(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Valid @ModelAttribute BoardRequest request) {
        log.info("게시글 등록 요청 - boardId: {}, title: {}", boardId, request.getTitle());
        return ResponseEntity.ok(boardService.createPost(boardId, request));
    }

    @Operation(summary = "게시글 수정",
            description = """
                    상세 페이지의 수정 화면에서 저장할 때 호출합니다(multipart/form-data). 관리자 또는 작성자 본인만 가능합니다.
                    content 는 등록과 동일하게 **마크다운 문자열**로 받습니다(HTML 아님).
                    첨부는 증분 방식 — 삭제할 기존 첨부의 id 만 deleteAttachmentIds 로, 새로 추가할 파일만 files 로 보내고,
                    유지할 기존 첨부는 아무것도 보내지 않습니다.
                    응답은 message 만 반환합니다(postId 없음) — 수정 후 프론트가 상세 GET 을 다시 하므로 상세 객체는 내려가지 않습니다.

                    ### 요청 예시
                    ```
                    PUT /api/user/boards/2/posts/171    (multipart/form-data)

                    title: 수정 제목                    ← 필수, 200자 이내
                    content: ## 수정된 마크다운 본문     ← 필수, 마크다운 문자열
                    categoryId: 4
                    isAnonymous: false
                    deleteAttachmentIds: 18, 19         ← 삭제할 기존 첨부 id만
                    files: 새파일.pdf                   ← 새로 추가할 첨부만
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "게시글이 성공적으로 수정되었습니다."}
                    ```

                    실패: 400 {"message":"제목은 필수입니다."} (등록과 동일한 검증 3종)
                    실패: 403 {"message":"수정 권한이 없습니다."}
                    실패: 404 {"message":"수정할 수 없는 게시글입니다."} (블라인드/삭제 글 — 작성자도 수정 불가)
                    """)
    @PutMapping(value = "/posts/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BoardResponse> updatePost(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @Valid @ModelAttribute BoardUpdateRequest request) {
        log.info("게시글 수정 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.updatePost(boardId, postId, request));
    }

    @Operation(summary = "게시글 삭제",
            description = """
                    상세 페이지에서 작성자 본인이 자기 글을 삭제할 때 호출합니다(소프트삭제).
                    관리자 조치(벌점·로그 연동)는 이 API 가 아니라 /api/admin/posts/{postId} 를 사용합니다.

                    ### 요청 예시
                    ```
                    PATCH /api/user/boards/2/posts/171/delete    (본문 없음)
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "게시글이 성공적으로 삭제되었습니다."}
                    ```

                    실패: 403 {"message":"본인 글만 삭제할 수 있습니다. (관리자 조치는 관리자 게시글 관리에서)"}
                    실패: 404 {"message":"존재하지 않는 게시글입니다."} (타 게시판 글·블라인드 글)
                    """)
    @PatchMapping("/posts/{postId}/delete")
    public ResponseEntity<BoardResponse> deletePost(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId) {
        log.info("게시글 삭제 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.deletePost(boardId, postId));
    }

    @Operation(summary = "댓글 목록 조회",
            description = """
                    게시글 상세 페이지에서 본문과 별도로 댓글 영역을 그릴 때 호출합니다.
                    state=normal 댓글만 내려갑니다 — 블라인드·삭제된 댓글은 목록에 포함되지 않으며,
                    익명 댓글은 작성자가, 비밀댓글은 내용이 서버에서 마스킹되어 반환됩니다(마스킹은 전부 서버 책임).

                    ### 요청 예시
                    ```
                    GET /api/user/boards/2/posts/171/comments
                    ```

                    ### 응답 예시
                    ```json
                    [
                      {"commentId": 200, "parentCommentId": null, "content": "댓글", "authorName": "관리자", "userId": 84,
                       "isAnonymous": false, "isPrivate": false,
                       "created": "2026-08-14T10:30:00", "updated": null}
                    ]
                    ```
                    ※ 익명댓글: authorName="익명", userId=null (관리자·댓글작성자 제외)
                    ※ 비밀댓글: content="비밀댓글입니다." (관리자·댓글작성자·원글작성자 제외)
                    ※ 대댓글은 parentCommentId 로 표현 (무제한 깊이)
                    """)
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommentDetailResponse>> getComments(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId) {
        log.info("댓글 목록 조회 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.getComments(boardId, postId));
    }

    @Operation(summary = "댓글/대댓글 등록",
            description = """
                    상세 페이지의 댓글 입력창에서 등록할 때 호출합니다.
                    parentCommentId 를 넣으면 그 댓글의 대댓글(답글)로 등록됩니다(무제한 깊이).
                    isAnonymous(자유게시판 익명)·isPrivate(정보게시판 비밀댓글)는 해당 옵션을 허용한 게시판에서만 의미가 있습니다.
                    등록되면 원글/부모 댓글 작성자에게 알림이 발행됩니다.

                    ### 요청 예시
                    ```json
                    {"content": "댓글 내용", "parentCommentId": null,
                     "isAnonymous": false, "isPrivate": false}
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "댓글이 성공적으로 등록되었습니다."}
                    ```

                    실패: 400 {"message":"답글을 달 부모 댓글이 존재하지 않습니다."}
                    실패: 403 {"message":"이 게시판은 댓글을 사용하지 않습니다."}
                    """)
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @Valid @RequestBody CommentRequest request) {
        log.info("댓글 등록 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.createComment(boardId, postId, request));
    }

    @Operation(summary = "댓글 수정",
            description = """
                    댓글의 수정 버튼으로 내용을 고칠 때 호출합니다. 관리자 또는 작성자 본인만 가능합니다.

                    ### 요청 예시
                    ```json
                    {"content": "수정된 댓글", "isAnonymous": false, "isPrivate": false}
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "댓글이 성공적으로 수정되었습니다."}
                    ```
                    """)
    @PutMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "189") @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request) {
        log.info("댓글 수정 요청 - boardId: {}, commentId: {}", boardId, commentId);
        return ResponseEntity.ok(boardService.updateComment(boardId, commentId, request));
    }

    @Operation(summary = "댓글 삭제",
            description = """
                    자기 댓글의 삭제 버튼을 누를 때 호출합니다(소프트삭제). 작성자 본인만 가능합니다.

                    ### 요청 예시
                    ```
                    PATCH /api/user/boards/2/posts/171/comments/200/delete    (본문 없음)
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "댓글이 성공적으로 삭제되었습니다."}
                    ```

                    실패: 403 {"message":"본인 댓글만 삭제할 수 있습니다. (관리자 조치는 관리자 화면에서)"}
                    """)
    @PatchMapping("/posts/{postId}/comments/{commentId}/delete")
    public ResponseEntity<BoardResponse> deleteComment(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "189") @PathVariable Long commentId) {
        log.info("댓글 삭제 요청 - boardId: {}, commentId: {}", boardId, commentId);
        return ResponseEntity.ok(boardService.deleteComment(boardId, commentId));
    }
}
