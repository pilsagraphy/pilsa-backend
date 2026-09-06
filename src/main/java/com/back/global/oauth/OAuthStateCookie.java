package com.back.global.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OAuth 흐름을 시작한 브라우저와 콜백을 받는 브라우저가 같은지 묶어 두는 쿠키.
 *
 * state 를 Redis 에만 두면 "발급된 state 인가"는 알아도 "누구 브라우저가 시작했나"는 모른다.
 * 그러면 공격자가 자기 구글 계정으로 동의를 마친 뒤 콜백 URL(code+state)을 피해자에게 열게 해서
 * 피해자 세션에 자기 구글 계정을 붙일 수 있다 — 로그인 흐름에서는 성가신 정도지만,
 * "로그인하면 이 구글 계정을 연결해 준다" 흐름에서는 그대로 계정 탈취가 된다.
 *
 * 그래서 시작 시점에 HttpOnly 쿠키로 state 를 심고, 콜백에서 쿼리의 state 와 대조한다.
 * 다른 브라우저로 넘어온 콜백은 쿠키가 없어 걸러진다. 연결 대기 토큰도 같은 방식으로 묶는다.
 *
 * SameSite=Lax 인 이유: 구글이 콜백으로 되돌리는 건 최상위 이동(top-level GET)이라 Lax 쿠키가
 * 함께 실린다. Strict 로 두면 그 순간 쿠키가 빠져 정상 흐름도 막힌다.
 *
 * 두 쿠키 모두 Path=/api 다. 연결 대기 쿠키는 로그인 화면(/api/auth/google/pending), 회원가입 화면,
 * 로그인 뒤 마무리(/api/user/mypage/google/link)가 모두 읽어야 해서 더 좁힐 수 없다.
 */
@Component
public class OAuthStateCookie {

    public static final String STATE_COOKIE = "g_oauth_state";
    public static final String PENDING_LINK_COOKIE = "g_pending_link";

    private static final String COOKIE_PATH = "/api";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);   // OAuthStateService 의 TTL 과 같게
    private static final Duration PENDING_TTL = Duration.ofMinutes(10); // PendingLinkStore 의 TTL 과 같게

    // 운영(https)에서만 Secure — 로컬 http 에서 Secure 를 켜면 브라우저가 쿠키를 버린다
    @Value("${jwt.cookie.secure:false}")
    private boolean secure;

    public void bindState(HttpServletResponse response, String state) {
        set(response, STATE_COOKIE, state, STATE_TTL);
    }

    /** 콜백의 state 가 이 브라우저가 시작한 것인지. 검사 후에는 쿠키를 지운다(1회용). */
    public boolean consumeState(HttpServletRequest request, HttpServletResponse response, String state) {
        String bound = read(request, STATE_COOKIE);
        set(response, STATE_COOKIE, "", Duration.ZERO);
        return bound != null && !bound.isBlank() && bound.equals(state);
    }

    public void bindPendingLink(HttpServletResponse response, String token) {
        set(response, PENDING_LINK_COOKIE, token, PENDING_TTL);
    }

    public String readPendingLink(HttpServletRequest request) {
        return read(request, PENDING_LINK_COOKIE);
    }

    public void clearPendingLink(HttpServletResponse response) {
        set(response, PENDING_LINK_COOKIE, "", Duration.ZERO);
    }

    private void set(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
