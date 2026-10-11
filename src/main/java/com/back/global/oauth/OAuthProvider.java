package com.back.global.oauth;

import java.util.Locale;
import java.util.Set;

/**
 * 지원하는 소셜 로그인 제공자.
 *
 * user_social_accounts.provider 에 이 이름 그대로 저장한다.
 * 구글은 GoogleAccountService(캘린더까지 같이), 네이버·카카오는 SocialAccountService 가 다룬다 (2026-10-11).
 */
public final class OAuthProvider {

    public static final String GOOGLE = "GOOGLE";
    public static final String KAKAO = "KAKAO";
    public static final String NAVER = "NAVER";

    /** 경로 변수(naver/kakao)로 받는 제공자 — 구글은 자기 컨트롤러가 따로 있다 */
    private static final Set<String> PATH_PROVIDERS = Set.of(NAVER, KAKAO);

    private OAuthProvider() {
    }

    /** "naver" → "NAVER". 지원하지 않는 값이면 null */
    public static String fromPath(String path) {
        if (path == null) return null;
        String upper = path.trim().toUpperCase(Locale.ROOT);
        return PATH_PROVIDERS.contains(upper) ? upper : null;
    }

    /** 화면에 보여 줄 이름 */
    public static String label(String provider) {
        if (NAVER.equals(provider)) return "네이버";
        if (KAKAO.equals(provider)) return "카카오";
        return "구글";
    }
}
