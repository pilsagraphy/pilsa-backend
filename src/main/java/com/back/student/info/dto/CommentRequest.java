package com.back.student.info.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentRequest {
    private String content;
    private boolean isPrivate; // 비밀댓글 기능 추가
}