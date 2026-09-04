package com.back.admin.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 댓글 관리 목록 한 행
@Getter
@Setter
@Schema(description = "댓글 관리 목록 행")
public class AdminCommentListResponse {

    @Schema(description = "댓글 ID")
    private Long commentId;

    @Schema(description = "원글(게시글) ID — 원글 상세로 이동하는 링크용", example = "171")
    private Long postId;

    @Schema(description = "게시판 ID")
    private Long boardId;

    @Schema(description = "게시판명 (boards.name, 한글)")
    private String boardName;

    @Schema(description = "작성자명 (관리자 화면에는 익명 댓글도 실제 작성자명 표시)")
    private String authorName;

    @Schema(description = "작성자 로그인 ID (조치 확인 모달 '대상 회원' 표기용, users.login_id)", example = "ch400")
    private String authorLoginId;

    @Schema(description = "작성자 학번 (조치 확인 모달 '대상 회원' 표기용, users.student_no)", example = "2026000000")
    private String authorStudentId;

    @Schema(description = "댓글 내용")
    private String content;

    @Schema(description = "작성일시")
    private LocalDateTime created;

    @Schema(description = "표시 상태 normal/blind (deleted 는 목록에서 제외)", example = "normal")
    private String state;
}
