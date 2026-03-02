package com.back.student.free.dto;

import lombok.Getter;
import lombok.Setter;

// 댓글 등록, 수정
@Getter
@Setter
public class CommentRequest {
    private String content;
    private boolean isAnonymous;
}