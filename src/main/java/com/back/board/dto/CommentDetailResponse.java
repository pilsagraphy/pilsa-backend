package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 상세페이지 댓글 정보 (자유/정보 통합).
 *  - isAnonymous : 자유게시판 익명 댓글 여부
 *  - isPrivate   : 정보게시판 비밀 댓글 여부
 */
@Getter
@Setter
public class CommentDetailResponse {
    private Long commentId;
    private Long parentCommentId;  // 대댓글이면 부모 댓글 ID, 최상위 댓글이면 null
    private String content;
    private String authorName;
    private Boolean isAnonymous;   // 익명 댓글 여부
    private Boolean isPrivate;     // 비밀댓글 여부
    private LocalDateTime created; // 작성 시각 (정렬·"n분 전" 표시용)
    private LocalDateTime updated; // 수정 시각 (미수정이면 null)
    private Long userId;           // 본인 확인용. 익명 댓글은 마스킹되어 null
}
