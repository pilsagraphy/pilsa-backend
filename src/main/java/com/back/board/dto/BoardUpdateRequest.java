package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

// 게시글 수정 요청 (공지/자유/정보 통합)
@Getter
@Setter
public class BoardUpdateRequest {
    private String title;
    private String content;
    private boolean isAnonymous;   // 자유게시판 익명 여부
    private boolean isPinned;      // 공지사항 중요표시 여부
    private Long categoryId;       // 자유/정보게시판 카테고리
}
