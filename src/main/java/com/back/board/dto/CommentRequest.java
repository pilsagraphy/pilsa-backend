package com.back.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 댓글 등록/수정 요청 (자유/정보 통합).
 *  - parentCommentId : 대댓글(답글)일 경우 부모 댓글 ID (없으면 최상위 댓글)
 *  - isAnonymous     : 자유게시판 익명 댓글
 *  - isPrivate       : 정보게시판 비밀 댓글
 */
@Getter
@Setter
public class CommentRequest {

    @Schema(description = "댓글 내용 (필수)", example = "좋은 글이네요!")
    @NotBlank(message = "댓글 내용은 필수입니다.")
    @Size(max = 1000, message = "댓글은 1000자를 넘을 수 없습니다.")
    private String content;

    @Schema(description = "대댓글(답글)일 경우 부모 댓글 ID. 최상위 댓글이면 미입력. 답글의 답글도 가능(무제한 깊이)",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private Long parentCommentId;

    // Boolean 래퍼 사용 이유는 BoardRequest 참고 (프로퍼티명 isXxx 유지)
    @Schema(description = "익명 여부 (익명 허용 게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private Boolean isAnonymous = false;

    @Schema(description = "비밀댓글 여부 (비밀댓글 허용 게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private Boolean isPrivate = false;
}
