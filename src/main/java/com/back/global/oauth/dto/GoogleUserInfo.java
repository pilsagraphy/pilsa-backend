package com.back.global.oauth.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * id_token 에서 꺼낸 구글 계정 정보.
 *
 * 계정 식별은 반드시 sub 로 한다 — 이메일은 사용자가 바꿀 수 있고,
 * 해지된 이메일이 다른 사람에게 재할당될 수도 있어 식별자로 쓰면 위험하다.
 */
@Getter
@Setter
public class GoogleUserInfo {
    private String sub;
    private String email;
    private boolean emailVerified;
    private String name;
}
