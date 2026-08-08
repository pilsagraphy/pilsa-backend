package com.back.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 댓글 등록/수정 요청 (자유/정보 통합).
 *  - isAnonymous : 자유게시판 익명 댓글
 *  - isPrivate   : 정보게시판 비밀 댓글
 */
@Getter
@Setter
public class CommentRequest {

    @Schema(description = "댓글 내용", example = "좋은 글이네요!")
    private String content;

    @Schema(description = "익명 여부 (자유게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private boolean isAnonymous;

    @Schema(description = "비밀댓글 여부 (정보게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private boolean isPrivate;
}
