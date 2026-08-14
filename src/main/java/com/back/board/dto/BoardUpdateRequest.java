package com.back.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

// 게시글 수정 요청 (공지/자유/정보 통합)
@Getter
@Setter
public class BoardUpdateRequest {

    @Schema(description = "제목", example = "수정된 제목")
    private String title;

    @Schema(description = "내용", example = "수정된 본문입니다.")
    private String content;

    // Boolean 래퍼 사용 이유는 BoardRequest 참고 (프로퍼티명 isXxx 유지)
    @Schema(description = "익명 여부 (익명 허용 게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private Boolean isAnonymous = false;

    // isPinned 는 요청으로 받지 않는다 — 카테고리('중요')로 서버가 결정. 등록(BoardRequest)과 동일 규칙
    // 중요 → 일반 카테고리로 바꾸면 상단 고정도 자동 해제된다

    @Schema(description = "카테고리 ID (자유/정보게시판). 공지사항은 미사용")
    private Long categoryId;
}
