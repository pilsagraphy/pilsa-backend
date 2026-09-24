package com.back.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

// 댓글 등록/수정 결과 메시지 응답
@Getter
@Setter
@AllArgsConstructor
public class CommentResponse {
    private String message;
}
