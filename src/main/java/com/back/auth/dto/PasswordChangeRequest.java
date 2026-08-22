package com.back.auth.dto;

import lombok.Getter;
import lombok.Setter;

// 마이페이지 비밀번호 변경 — 현재 비밀번호 재확인 필수 (비로그인 초기화 PasswordResetRequest 와 별개).
// 새 비밀번호 재입력(확인)은 api_endpoints 정본상 프론트 검증이라 필드로 받지 않는다.
@Getter
@Setter
public class PasswordChangeRequest {
    private String currentPassword;
    private String newPassword;
}
