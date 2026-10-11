package com.back.global.oauth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 네이버·카카오가 알려 준 사용자 — 식별은 id(provider 안에서 유일)로, 이메일은 표시·안내용.
 * 카카오는 이메일이 선택 동의라 null 일 수 있다.
 */
@Getter
@AllArgsConstructor
public class SocialUserInfo {
    private final String id;
    private final String email;
    private final boolean emailVerified;
    private final String name;
}
