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

    @Schema(description = "익명 여부 (자유게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private boolean isAnonymous;

    @Schema(description = "중요표시 여부 (공지사항 전용). 그 외 게시판은 무시됨", example = "false")
    private boolean isPinned;

    @Schema(description = "카테고리 ID (자유/정보게시판). 공지사항은 미사용")
    private Long categoryId;
}
