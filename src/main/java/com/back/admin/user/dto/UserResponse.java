package com.back.admin.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 회원 관리 작업(수정 등) 공통 응답
@Getter
@AllArgsConstructor
public class UserResponse {
    private String message;
    private Long userId;
}
