package com.back.student.free.dto;

import lombok.Getter;
import lombok.Setter;

// 게시글 수정
@Getter
@Setter
public class FreeUpdateRequest {
    private String title;
    private String content;
    private boolean isAnonymous;
    private Long categoryId;
}