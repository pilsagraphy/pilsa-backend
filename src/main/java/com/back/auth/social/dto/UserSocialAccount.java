package com.back.auth.social.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * user_social_accounts 행 — 회원 계정에 연결된 소셜 계정 하나.
 *
 * 식별은 항상 providerUserId 로 한다(구글 sub 등). 이메일은 바뀔 수 있어 식별자로 쓰지 않는다.
 * providerEmail 은 표시용이며 카카오처럼 선택 동의인 곳에서는 null 일 수 있다.
 */
@Getter
@Setter
public class UserSocialAccount {
    private Long id;
    private Long userId;
    private String provider;        // OAuthProvider.GOOGLE 등
    private String providerUserId;
    private String providerEmail;
    private LocalDateTime linkedAt;
}
