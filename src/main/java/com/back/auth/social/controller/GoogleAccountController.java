package com.back.auth.social.controller;

import com.back.global.security.AuthUtils;
import com.back.auth.social.dto.GoogleLinkStatusResponse;
import com.back.auth.social.service.GoogleAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 마이페이지 - 구글 계정 연결 관리.
 *
 * 연결을 시작하는 API 만 여기 있고, 완료는 로그인과 같은 콜백
 * (`GET /api/auth/google/callback`)이 state 로 구분해 처리한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-구글계정")
public class GoogleAccountController {

    private final GoogleAccountService accountService;

    @Operation(summary = "구글 계정 연결 상태 조회",
            description = """
                    마이페이지에서 연결 버튼/해제 버튼 중 무엇을 그릴지 판단할 때 호출한다.

                    ### 응답 예시
                    ```json
                    {"linked":true,"googleEmail":"hong@gmail.com","linkedAt":"2026-08-23 14:02:11"}
                    ```
                    연결이 없으면 `{"linked":false,"googleEmail":null,"linkedAt":null}`""")
    @GetMapping("/api/user/mypage/google")
    public ResponseEntity<GoogleLinkStatusResponse> status() {
        return ResponseEntity.ok(accountService.getLinkStatus(AuthUtils.currentUserId()));
    }

    @Operation(summary = "구글 계정 연결 - 동의 화면 URL 발급",
            description = """
                    [구글 계정 연결] 버튼이 호출한다. 받은 URL 로 이동시키면 구글 동의 후
                    `{프론트}/mypage?google=linked` 로 돌아온다.

                    ### 응답 예시
                    ```json
                    {"authorizeUrl":"https://accounts.google.com/o/oauth2/v2/auth?...&state=..."}
                    ```
                    이미 다른 회원에게 연결된 구글 계정을 고르면 콜백에서
                    `{프론트}/mypage?google=already_linked` 로 돌아온다 (그 외 실패는 `?google=failed`).
                    로그인 화면으로 보내지 않는다 — 이 흐름은 이미 로그인한 사용자가 시작한 것이다.""")
    @GetMapping("/api/user/mypage/google/authorize")
    public ResponseEntity<Map<String, String>> authorize() {
        String url = accountService.buildLinkAuthorizeUrl(AuthUtils.currentUserId());
        return ResponseEntity.ok(Map.of("authorizeUrl", url));
    }

    @Operation(summary = "구글 계정 연결 해제",
            description = """
                    연결을 끊는다. 캘린더 연동이 남아 있으면 함께 해제된다 —
                    계정 연결이 사라지면 캘린더 토큰을 붙여 둘 곳이 없기 때문이다.

                    해제 후에는 아이디/비밀번호로만 로그인할 수 있다.

                    ### 응답 예시
                    ```json
                    {"message":"구글 계정 연결을 해제했습니다."}
                    ```""")
    @DeleteMapping("/api/user/mypage/google")
    public ResponseEntity<Map<String, String>> unlink() {
        accountService.unlinkAccount(AuthUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "구글 계정 연결을 해제했습니다."));
    }
}
