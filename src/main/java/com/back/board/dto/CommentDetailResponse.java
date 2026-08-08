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
    private boolean isAnonymous;   // 자유게시판 익명 여부
    private boolean isPrivate;     // 정보게시판 비밀댓글 여부
    private LocalDateTime updated;
    private Long userId;           // 본인 확인용
}
