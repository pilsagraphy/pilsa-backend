package com.back.board.controller;

import com.back.board.dto.*;
import com.back.board.service.BoardService;
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
 * 예) GET /api/boards/2/posts → 자유게시판 전체 조회
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards/{boardId}")
@Tag(name = "게시판", description = "게시판 통합 API. boardId 로 게시판 구분(기본: 1=공지사항, 2=자유게시판, 3=정보게시판). 관리자가 추가한 게시판도 동일 경로 사용")
public class BoardController {

    private final BoardService boardService;

    // boardId 경로변수 공통 설명 (모든 엔드포인트에서 재사용)
    private static final String BOARD_ID_DESC = "게시판 ID (기본 1=공지사항, 2=자유게시판, 3=정보게시판. /api/boards 로 목록 조회)";

    @Operation(summary = "카테고리 목록 조회",
            description = "게시판의 카테고리 목록을 조회합니다. 카테고리를 쓰지 않는 게시판은 빈 목록이 반환됩니다.")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId) {
        log.info("게시판 카테고리 목록 조회 요청 - boardId: {}", boardId);
        List<CategoryResponse> responses = boardService.getCategoryList(boardId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "상단 5개 조회",
            description = "메인 화면용 상단 5개 글을 조회합니다. 중요표시(is_pinned) 글이 우선 정렬됩니다.")
    @GetMapping("/posts/top5")
    public ResponseEntity<List<BoardTop5Response>> getTop5Posts(
            @Parameter(description = BOARD_ID_DESC, example = "1") @PathVariable Long boardId) {
        log.info("메인 화면용 상단 5개 조회 요청 - boardId: {}", boardId);
        List<BoardTop5Response> responses = boardService.getTop5Posts(boardId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "게시글 전체 조회 (페이징/검색/정렬)",
            description = "게시판의 글 목록을 페이징하여 조회합니다. categoryId(카테고리 필터), keyword(제목·내용 검색), sort(created=최신순, viewCount=조회순) 지원.")
    @GetMapping("/posts")
    public ResponseEntity<BoardPageResponse> getAllPosts(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "한 페이지당 글 개수", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "카테고리 필터 (선택). 공지사항은 미사용")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "검색어 (제목·내용, 선택)")
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
            description = "게시글 1건의 상세 정보를 조회합니다. 조회수가 1 증가하며, 첨부파일·좋아요·댓글·이전글/다음글 링크를 함께 반환합니다.")
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
            description = "게시글 좋아요를 토글합니다. 이미 눌렀으면 취소, 안 눌렀으면 추가됩니다.")
    @PatchMapping("/posts/{postId}/like")
    public ResponseEntity<BoardResponse> toggleLike(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId) {
        log.info("게시글 좋아요 토글 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.togglePostLike(boardId, postId));
    }

    @Operation(summary = "게시글 등록 (파일 업로드)",
            description = "게시글을 등록합니다(multipart/form-data). 공지사항(boardId=1)은 관리자만 등록 가능하며, "
                    + "isPinned(공지 중요표시)·isAnonymous(자유게시판 익명)는 해당 게시판에서만 의미가 있습니다.")
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BoardResponse> createPost(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @ModelAttribute BoardRequest request) {
        log.info("게시글 등록 요청 - boardId: {}, title: {}", boardId, request.getTitle());
        return ResponseEntity.ok(boardService.createPost(boardId, request));
    }

    @Operation(summary = "게시글 수정",
            description = "게시글을 수정합니다. 관리자 또는 작성자 본인만 가능합니다.")
    @PutMapping("/posts/{postId}")
    public ResponseEntity<BoardResponse> updatePost(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @RequestBody BoardUpdateRequest request) {
        log.info("게시글 수정 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.updatePost(boardId, postId, request));
    }

    @Operation(summary = "게시글 삭제",
            description = "게시글을 삭제합니다. 공지사항(boardId=1)은 관리자만, 자유·정보게시판은 작성자 본인만 삭제할 수 있습니다.")
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<BoardResponse> deletePost(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId) {
        log.info("게시글 삭제 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.deletePost(boardId, postId));
    }

    @Operation(summary = "댓글/대댓글 등록",
            description = "게시글에 댓글을 등록합니다. parentCommentId를 넣으면 그 댓글의 대댓글(답글)로 등록됩니다(무제한 깊이). "
                    + "isAnonymous(자유게시판 익명)·isPrivate(정보게시판 비밀댓글)는 해당 게시판에서만 의미가 있습니다.")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @RequestBody CommentRequest request) {
        log.info("댓글 등록 요청 - boardId: {}, postId: {}", boardId, postId);
        return ResponseEntity.ok(boardService.createComment(boardId, postId, request));
    }

    @Operation(summary = "댓글 수정",
            description = "댓글을 수정합니다. 관리자 또는 작성자 본인만 가능합니다.")
    @PutMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "189") @PathVariable Long commentId,
            @RequestBody CommentRequest request) {
        log.info("댓글 수정 요청 - boardId: {}, commentId: {}", boardId, commentId);
        return ResponseEntity.ok(boardService.updateComment(boardId, commentId, request));
    }

    @Operation(summary = "댓글 삭제",
            description = "댓글을 삭제합니다. 작성자 본인만 가능합니다.")
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<BoardResponse> deleteComment(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "게시글 ID", example = "140") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "189") @PathVariable Long commentId) {
        log.info("댓글 삭제 요청 - boardId: {}, commentId: {}", boardId, commentId);
        return ResponseEntity.ok(boardService.deleteComment(boardId, commentId));
    }
}
