package com.back.global.oauth;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 구글 연동 설정 묶음 (소셜 로그인 + 캘린더 공용).
 *
 * client-secret 과 토큰 암호화 키는 절대 application.properties 에 커밋하지 않는다 —
 * 환경변수로 주입한다. properties 에는 ${GOOGLE_CLIENT_SECRET} 형태의 참조만 둔다.
 *
 * 리다이렉트 URI 가 로그인용/캘린더용으로 갈리는 이유:
 *  - 로그인은 스코프가 openid/email/profile 이고 비로그인 상태에서 시작한다
 *  - 캘린더는 calendar.events 스코프에 access_type=offline 이 필요하고 로그인 상태에서 시작한다
 * 구글은 리다이렉트 URI 를 사전 등록된 값과 정확히 대조하므로 두 개를 따로 등록해야 한다.
 */
@Getter
@Component
public class GoogleProperties {

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    /** 소셜 로그인 콜백 (예: https://pilsa.co.kr/api/auth/google/callback) */
    @Value("${google.oauth.login-redirect-uri}")
    private String loginRedirectUri;

    /** 캘린더 연동 콜백 (예: https://pilsa.co.kr/api/user/mypage/calendar/google/callback) */
    @Value("${google.oauth.calendar-redirect-uri}")
    private String calendarRedirectUri;

    /** 콜백 처리 후 사용자를 돌려보낼 프론트 주소 */
    @Value("${google.oauth.frontend-url}")
    private String frontendUrl;

    /** refresh token 암호화 키 (Base64 인코딩된 32바이트 = AES-256) */
    @Value("${google.token.cipher-key}")
    private String tokenCipherKey;

    // ─────────────────────── 스코프 ───────────────────────
    // 로그인과 캘린더를 한 번에 요청하지 않는다(증분 승인).
    // 캘린더 권한은 사용자가 그 기능을 쓰겠다고 했을 때만 물어야 동의율이 올라가고,
    // 로그인만 하려는 사용자에게 캘린더 권한을 요구하면 이탈한다.
    public static final String LOGIN_SCOPE = "openid email profile";
    // 캘린더에도 openid·email 을 같이 요청한다 — 어느 구글 계정 캘린더인지 저장하려고 id_token 의 이메일을 읽는데,
    // 구글은 scope 에 openid 가 있어야 id_token 을 준다. 전에는 소셜 로그인 때 받아 둔 openid 동의가
    // include_granted_scopes 로 합쳐져 우연히 됐고, 연동 해제(토큰 폐기 = 그 계정의 이 앱 동의 전부 취소) 뒤 재연동하면
    // "구글 응답에 id_token 이 없습니다" 로 실패했다 (PM 2026-10-11 16:23 운영 로그).
    public static final String CALENDAR_SCOPE = "openid email https://www.googleapis.com/auth/calendar.events";
}
