package com.back.auth.dto;

import lombok.Getter;
import lombok.Setter;

// 마이페이지 비밀번호 변경 — 현재 비밀번호 재확인 필수 (비로그인 초기화 PasswordResetRequest 와 별개)
@Getter
@Setter
public class PasswordChangeRequest {
    private String currentPassword;
    private String newPassword;
    // 선택값 — api_endpoints 정본상 재입력 확인은 프론트 검증이라 API 로 보내지 않는다.
    // 보내온 경우에만 서버가 대조한다.
    private String newPasswordConfirm;
}
