package com.back.auth.controller;

import com.back.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 내 세션 관리.
 *
 * 경로가 /api/user/mypage/** 인데 auth 패키지에 있는 이유: 토큰·세션은 auth 도메인 소유이고,
 * 마이페이지 경로를 auth 가 서비스하는 선례가 이미 있다(WithdrawController 의 /api/user/mypage/withdraw).
 * /api/auth/** 에 두면 안 된다 — 그쪽은 SecurityConfig 에서 permitAll 이라 누구 세션인지 알 수 없다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "인증(로그인·회원가입·계정찾기)")
public class SessionController {

    private final AuthService authService;

    @Operation(summary = "모든 기기에서 로그아웃",
            description = """
                    지금까지 로그인한 **모든 기기**의 세션을 끊는다. 마이페이지 보안 항목에서 호출한다.
                    비밀번호를 다시 받지 않는다 — 이미 로그인한 본인만 호출할 수 있고, 되돌릴 수 없는 조치도 아니다.

                    ### 언제 쓰나
                    공용 PC 에서 로그아웃을 깜빡했거나, 계정이 털린 것 같을 때. 자동 로그인 세션이 400일짜리라
                    이 API 가 없으면 남의 기기에 남은 세션을 끊을 방법이 없다.

                    ### 처리 내용
                    - `users.token_version` 을 +1 → 그 전에 발급된 액세스·리프레시 토큰이 **전부 무효**가 된다
                    - 다른 기기는 다음 요청에서 401 `X-Token-Expired: true` 를 받고 로그인 화면으로 떨어진다
                    - 지금 이 기기의 refreshToken 쿠키도 함께 만료된다 (즉, 호출한 본인도 로그아웃된다)

                    > 비밀번호 변경(`PUT /api/auth/password/reset`)은 이 처리를 **자동으로** 수행하므로
                    > 따로 호출할 필요가 없다.

                    ### 응답 예시
                    ```json
                    {"message":"모든 기기에서 로그아웃되었습니다."}
                    ```
                    실패: 401 {"message":"인증이 필요합니다. (Authorization 헤더 누락 또는 유효하지 않은 토큰)"}
                    """)
    @PatchMapping("/api/user/mypage/logout-all")
    public ResponseEntity<Map<String, String>> logoutAllDevices(HttpServletRequest request,
                                                                HttpServletResponse response) {
        authService.logoutAllDevices(request, response);
        return ResponseEntity.ok(Map.of("message", "모든 기기에서 로그아웃되었습니다."));
    }
}
