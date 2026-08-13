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

    @Schema(description = "중요표시(상단 고정) 여부. 관리자(레벨 1~3)만 설정 가능", example = "false")
    private Boolean isPinned = false;

    @Schema(description = "카테고리 ID (자유/정보게시판). 공지사항은 미사용")
    private Long categoryId;
}
