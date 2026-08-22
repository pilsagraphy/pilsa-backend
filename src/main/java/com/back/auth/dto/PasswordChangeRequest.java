package com.back.auth.dto;

import lombok.Getter;
import lombok.Setter;

// 마이페이지 비밀번호 변경 — 현재 비밀번호 재확인 필수 (비로그인 초기화 PasswordResetRequest 와 별개)
@Getter
@Setter
public class PasswordChangeRequest {
    private String currentPassword;
    private String newPassword;
    private String newPasswordConfirm;
}
