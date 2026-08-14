package com.back.board.controller;

import com.back.board.dto.BoardSummaryResponse;
import com.back.board.service.BoardPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 게시판 목록 (네비게이션/사이드바용).
 *
 * 게시판이 데이터가 되었으므로 프론트가 게시판 종류를 하드코딩할 수 없다.
 * 이 API로 "현재 사용자가 볼 수 있는 게시판"만 받아서 메뉴를 그린다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards")
@Tag(name = "게시판", description = "게시판 목록 — 현재 사용자가 열람 가능한 게시판만 반환")
public class BoardListController {

    private final BoardPolicyService boardPolicyService;

    @Operation(summary = "게시판 목록 조회",
            description = "노출 순서(display_order)대로, 현재 사용자의 신분·관리레벨로 열람 가능한 게시판만 반환합니다. 비로그인은 전체공개 게시판만 보입니다.")
    @GetMapping
    public ResponseEntity<List<BoardSummaryResponse>> getBoards() {
        return ResponseEntity.ok(boardPolicyService.getReadableBoards());
    }
}
