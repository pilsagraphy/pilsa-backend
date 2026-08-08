package com.back.board.dto;

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
    private String content;
    private boolean isAnonymous;   // 자유게시판 익명 여부
    private boolean isPrivate;     // 정보게시판 비밀댓글 여부
}
