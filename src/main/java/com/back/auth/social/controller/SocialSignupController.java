package com.back.auth.social.controller;

import com.back.auth.core.dto.AuthResponse;
import com.back.auth.local.dto.SignupRequest;
import com.back.auth.social.service.SocialSignupService;
import com.back.global.oauth.OAuthStateCookie;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 없는 소셜 가입 — 구글·네이버·카카오 공통 (PM 2026-10-13) */
@RestController
@RequiredArgsConstructor
@Tag(name = "인증-소셜 가입", description = "소셜 로그인 뒤 미가입 회원의 비밀번호 없는 가입")
public class SocialSignupController {

    private final SocialSignupService signupService;
    private final OAuthStateCookie stateCookie;

    @Operation(summary = "소셜 가입 (비밀번호 없음)",
            description = """
                    [네이버/카카오/구글로 로그인] → 연결된 회원 없음 → `/login?socialLink=naver`(구글은 `googleLink=1`) 로 돌아온 뒤
                    회원가입 화면에서 호출한다. 보관 토큰은 콜백이 심은 HttpOnly 쿠키(`g_pending_link`)에서 읽으므로 본문에 없다.

                    본문은 `POST /api/auth/register` 와 같되 `password` 가 없다 — name, phone, major, studentNo, email, loginId, memberType.
                    이메일 인증번호 통과·중복·재가입 대조는 보통 가입과 똑같이 본다. 아이디는 사용자가 직접 정한다.

                    성공하면 users 행(password_hash='SOCIAL_ONLY') + 소셜 계정 연결 + **바로 로그인**(리프레시 쿠키, 자동 로그인) 이 한 번에 된다.
                    응답은 로그인과 같은 AuthResponse. 실패: 400 간편 로그인 대기 없음·만료 / 그 외는 /register 와 동일.
                    가입 후 아이디·비밀번호 로그인은 안 되고(403 안내문), 마이페이지 > 설정 > "비밀번호 만들기" 로 보통 회원이 될 수 있다.
                    """)
    @PostMapping("/api/auth/register/social")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse response) {
        SocialSignupService.Result result = signupService.signup(request, stateCookie.readPendingLink(httpRequest), response);
        stateCookie.clearPendingLink(response);
        return ResponseEntity.ok(result.auth());
    }
}
