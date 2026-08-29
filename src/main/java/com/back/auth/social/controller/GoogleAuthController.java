package com.back.auth.social.controller;

import com.back.global.oauth.GoogleProperties;
import com.back.global.oauth.GoogleIntegrationException;
import com.back.auth.social.service.GoogleAccountService;
import com.back.global.oauth.OAuthStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 구글 소셜 로그인.
 *
 * 로그인과 계정 연결이 같은 콜백을 쓴다 — 구글은 리다이렉트 URI 를 사전 등록된 값과 정확히
 * 대조하므로 경로를 늘릴수록 등록·운영이 번거로워진다. 목적 구분은 state 로 한다.
 *
 * 콜백은 accessToken 을 URL 에 실어 보내지 않는다. 브라우저 히스토리·중간 로그·리퍼러에 남기 때문이다.
 * 대신 refreshToken 쿠키만 심고 프론트로 돌려보내면, 프론트가 기존
 * `POST /api/auth/token/access/refresh` 로 accessToken 을 받아 간다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "로그인-구글", description = "구글 소셜 로그인 (연결된 계정으로만 로그인 가능 — 구글만으로는 가입되지 않는다)")
public class GoogleAuthController {

    private final GoogleProperties properties;
    private final GoogleAccountService accountService;
    private final OAuthStateService stateService;

    @Operation(summary = "구글 로그인 - 동의 화면 URL 발급",
            description = """
                    로그인 화면의 [구글로 로그인] 버튼이 호출한다. 받은 URL 로 이동시키면 된다.

                    ### 응답 예시
                    ```json
                    {"authorizeUrl":"https://accounts.google.com/o/oauth2/v2/auth?client_id=...&state=..."}
                    ```
                    ```js
                    const { authorizeUrl } = await api.get('/api/auth/google/authorize');
                    window.location.href = authorizeUrl;
                    ```
                    state 는 5분간만 유효하므로 받은 즉시 이동시켜야 한다.""")
    @GetMapping("/api/auth/google/authorize")
    public ResponseEntity<Map<String, String>> authorize() {
        return ResponseEntity.ok(Map.of("authorizeUrl", accountService.buildLoginAuthorizeUrl()));
    }

    @Operation(summary = "구글 로그인 - 콜백 (구글이 호출)",
            description = """
                    구글이 브라우저를 이 주소로 돌려보낸다. **프론트가 직접 호출하는 API 가 아니다.**

                    처리 후 프론트로 302 리다이렉트한다. **state 의 목적에 따라 돌아가는 화면이 다르다** —
                    계정 연결은 이미 로그인한 사용자가 마이페이지에서 시작한 흐름이라 로그인 화면으로 보내지 않는다.

                    | 흐름 | 성공 | 실패 |
                    |---|---|---|
                    | 로그인 | `{프론트}/login?login=google` | `/login?error=GOOGLE_NOT_LINKED` (연결된 회원 없음) · `?error=GOOGLE_LOGIN_FAILED` |
                    | 계정 연결 | `{프론트}/mypage?google=linked` | `/mypage?google=already_linked` (그 구글 계정이 이미 다른 회원 것) · `?google=failed` |

                    state 자체가 만료·위조면 목적을 알 수 없으므로 `/login?error=INVALID_STATE`.
                    로그인 성공 시 refreshToken 쿠키만 심어 보내므로, 프론트는 그 쿼리를 보고
                    `POST /api/auth/token/access/refresh` 를 호출해 accessToken 을 받는다.

                    accessToken 을 쿼리로 넘기지 않는 이유는 브라우저 히스토리와 리퍼러에 남기 때문이다.""")
    @GetMapping("/api/auth/google/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         HttpServletResponse response) {
        // 사용자가 동의 화면에서 취소를 누른 경우
        if (error != null) {
            log.info("구글 동의 취소 또는 오류: {}", error);
            return redirect("/login", "error", "GOOGLE_CANCELLED");
        }

        // state 를 먼저 소비해 목적을 확보한다.
        // 목적을 모르면 실패했을 때 어디로 돌려보낼지 알 수 없다 — 계정 연결은 이미 로그인한 사용자가
        // 마이페이지에서 시작한 흐름이라 /login 으로 보내면 안 되고, 안내 문구도 로그인 실패와 정반대여야 한다.
        OAuthStateService.StateData data;
        try {
            data = stateService.consume(state);
        } catch (Exception e) {
            log.info("구글 콜백 state 검증 실패: {}", e.getMessage());
            return redirect("/login", "error", "INVALID_STATE");
        }

        boolean isLink = OAuthStateService.PURPOSE_LINK.equals(data.purpose());

        try {
            if (isLink) {
                if (data.userId() == null) {
                    return redirect("/mypage", "google", "failed");
                }
                accountService.linkAccount(data.userId(), code);
                return redirect("/mypage", "google", "linked");
            }

            accountService.loginWithGoogle(code, response);
            // 로그인 페이지로 돌려보낸다 — 프론트가 여기서 accessToken 을 받아 세션을 세우고 대시보드로 넘긴다
            return redirect("/login", "login", "google");

        } catch (GoogleIntegrationException e) {
            log.info("구글 콜백 실패 (purpose={}): {}", data.purpose(), e.getMessage());

            if (isLink) {
                // CONFLICT = 그 구글 계정이 이미 다른 회원에게 붙어 있다
                return redirect("/mypage", "google",
                        e.getStatus() == HttpStatus.CONFLICT ? "already_linked" : "failed");
            }
            // CONFLICT = 이 구글 계정에 연결된 회원이 없다
            return redirect("/login", "error",
                    e.getStatus() == HttpStatus.CONFLICT ? "GOOGLE_NOT_LINKED" : "GOOGLE_LOGIN_FAILED");

        } catch (Exception e) {
            log.error("구글 콜백 처리 중 예기치 못한 오류 (purpose={})", data.purpose(), e);
            return isLink
                    ? redirect("/mypage", "google", "failed")
                    : redirect("/login", "error", "GOOGLE_LOGIN_FAILED");
        }
    }

    private ResponseEntity<Void> redirect(String path, String key, String value) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getFrontendUrl())
                .path(path)
                .queryParam(key, value)
                .build().encode().toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toString())
                .build();
    }
}
