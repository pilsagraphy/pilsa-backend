package com.back.global.oauth;

import com.back.global.oauth.dto.SocialUserInfo;

/**
 * 네이버·카카오처럼 "동의 URL → 코드 교환 → 사용자 조회" 세 단계만 필요한 제공자의 공통 계약.
 * 구글은 id_token·캘린더까지 있어 GoogleOAuthClient 를 따로 둔다.
 */
public interface SocialOAuthClient {

    /** OAuthProvider 상수 */
    String provider();

    /** 서버 properties 에 키가 들어 있는가 — 없으면 로그인 버튼을 눌러도 503 로 안내한다 */
    boolean isConfigured();

    /** 콜백 주소 (콘솔에 등록한 값과 같아야 한다) */
    String loginRedirectUri();

    String buildAuthorizeUrl(String state);

    /** 인증 코드를 토큰으로 바꾸고 사용자 정보를 읽는다 */
    SocialUserInfo fetchUser(String code, String state);
}
