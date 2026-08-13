package com.back.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 게시글 등록/수정/삭제/좋아요 등 단순 결과 메시지 응답
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoardResponse {
    private String message;
}
