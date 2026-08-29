package com.back.global.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 구글 토큰 엔드포인트 응답.
 *
 * refresh_token 은 access_type=offline + prompt=consent 로 요청했을 때만 들어온다.
 * refresh 요청(grant_type=refresh_token)의 응답에는 없다 — 기존 것을 계속 쓴다.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("id_token")
    private String idToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    /** 구글이 실제로 승인한 스코프 (요청한 것과 다를 수 있다 — 사용자가 일부만 체크할 수 있음) */
    private String scope;

    @JsonProperty("token_type")
    private String tokenType;
}
