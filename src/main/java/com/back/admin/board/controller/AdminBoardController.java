package com.back.admin.board.controller;

import com.back.admin.board.dto.AdminBoardResponse;
import com.back.admin.board.dto.BoardSaveRequest;
import com.back.admin.board.service.AdminBoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 게시판 관리 (시안: 관리자 > 커뮤니티 관리 > 게시판 관리).
 * 게시판을 런타임에 생성/수정할 수 있으며, 열람권한·작성권한도 여기서 지정한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/boards")
@Tag(name = "관리자 - 게시판 관리", description = "게시판 생성/수정/삭제 및 열람·작성 권한 설정")
public class AdminBoardController {

    private final AdminBoardService adminBoardService;

    @Operation(summary = "게시판 목록", description = "게시판명·게시글 수·열람권한·작성권한·노출순서")
    @GetMapping
    public ResponseEntity<List<AdminBoardResponse>> getBoards() {
        return ResponseEntity.ok(adminBoardService.getBoards());
    }

    @Operation(summary = "새 게시판 생성")
    @PostMapping
    public ResponseEntity<AdminBoardResponse> createBoard(@RequestBody BoardSaveRequest request) {
        log.info("[관리자] 게시판 생성 - name: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(adminBoardService.createBoard(request));
    }

    @Operation(summary = "게시판 수정", description = "전달된 필드만 수정됩니다.")
    @PutMapping("/{boardId}")
    public ResponseEntity<AdminBoardResponse> updateBoard(
            @PathVariable Long boardId,
            @RequestBody BoardSaveRequest request) {
        log.info("[관리자] 게시판 수정 - boardId: {}", boardId);
        return ResponseEntity.ok(adminBoardService.updateBoard(boardId, request));
    }

    @Operation(summary = "게시판 삭제", description = "소프트 삭제. 글이 남아 있는 게시판은 삭제할 수 없습니다.")
    @DeleteMapping("/{boardId}")
    public ResponseEntity<Map<String, String>> deleteBoard(@PathVariable Long boardId) {
        log.info("[관리자] 게시판 삭제 - boardId: {}", boardId);
        adminBoardService.deleteBoard(boardId);
        return ResponseEntity.ok(Map.of("message", "게시판이 삭제되었습니다."));
    }
}
