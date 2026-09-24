package com.back.global.oauth;

/**
 * 지원하는 소셜 로그인 제공자.
 *
 * user_social_accounts.provider 에 이 이름 그대로 저장한다.
 * 카카오·네이버를 붙일 때 여기에 상수를 추가하고 provider 별 클라이언트를 만들면 된다.
 */
public final class OAuthProvider {

    public static final String GOOGLE = "GOOGLE";
    // public static final String KAKAO = "KAKAO";
    // public static final String NAVER = "NAVER";

    private OAuthProvider() {
    }
}
