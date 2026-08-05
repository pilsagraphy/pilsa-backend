package com.back.admin.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 조치 결과 메시지 응답 (기존 FreeResponse 와 동일한 형태)
@Getter
@AllArgsConstructor
public class AdminPostResponse {
    private String message;
}
