package com.back.auth.core;

/**
 * 비밀번호 없는 소셜 가입 회원 표식 (PM 2026-10-13).
 *
 * users.password_hash 는 NOT NULL 을 유지하고, 소셜로만 가입한 회원은 이 고정 문자열을 넣는다 —
 * NULL 허용으로 바꾸면 기존 일반 가입 경로까지 영향이 갈까 봐 PM 이 "NULL 말고 소셜로그인이라고 표시" 를 택했다.
 * bcrypt 해시 형식이 아니라 어떤 비밀번호로도 matches 가 참이 되지 않으므로 아이디·비밀번호 로그인은 원천 차단된다.
 * 마이페이지에서 비밀번호를 만들면 보통 회원과 똑같아진다.
 */
public final class SocialOnlyPassword {

    public static final String MARKER = "SOCIAL_ONLY";

    private SocialOnlyPassword() {
    }

    public static boolean is(String passwordHash) {
        return MARKER.equals(passwordHash);
    }
}
