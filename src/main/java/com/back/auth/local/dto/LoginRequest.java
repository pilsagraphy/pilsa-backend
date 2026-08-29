package com.back.auth.local.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginRequest {
    private String loginId;
    private String password;

    // Boolean 래퍼 사용 이유: primitive 면 프로퍼티명이 autoLogin 이 아니라 그대로지만,
    // 미전달과 false 를 구분할 수 없어 "기본값을 서버가 정한다"는 의도가 코드에 남지 않는다.
    // (null = 미전달 → 자동 로그인 아님)
    @Schema(description = "자동 로그인 체크 여부. true 면 refreshToken 쿠키가 policy_settings.auto_login_days(400일) 동안 유지된다. "
            + "미전달·false 면 세션 쿠키(브라우저 종료 시 소멸) + 리프레시 토큰 12시간",
            example = "false")
    private Boolean autoLogin;
}
