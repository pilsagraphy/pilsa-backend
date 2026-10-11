package com.back.global.oauth;

import com.back.global.oauth.dto.SocialUserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 네이버 로그인 (네이버 개발자센터 > Pilsagraphy). REST 로 직접 호출한다.
 *  - 동의:   https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id&redirect_uri&state
 *  - 토큰:   https://nid.naver.com/oauth2.0/token?grant_type=authorization_code&client_id&client_secret&code&state
 *  - 사용자: https://openapi.naver.com/v1/nid/me (Bearer) → response.id / email / name
 * 제공 정보는 콘솔에서 '회원이름·연락처 이메일' 필수로 설정했다 (PM 2026-10-11). scope 파라미터는 없다.
 * 키가 비어 있으면(아직 properties 에 안 넣음) isConfigured=false 로 버튼이 안내만 한다.
 */
@Slf4j
@Component
public class NaverOAuthClient implements SocialOAuthClient {

    private static final String AUTH_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String ME_URL = "https://openapi.naver.com/v1/nid/me";

    @Value("${naver.oauth.client-id:}")
    private String clientId;

    @Value("${naver.oauth.client-secret:}")
    private String clientSecret;

    @Value("${naver.oauth.login-redirect-uri:https://pilsa.co.kr/api/auth/naver/callback}")
    private String loginRedirectUri;

    private final RestClient restClient = RestClient.create();

    @Override
    public String provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String loginRedirectUri() {
        return loginRedirectUri;
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", loginRedirectUri)
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Override
    public SocialUserInfo fetchUser(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new GoogleIntegrationException("네이버 인증 코드가 없습니다.", HttpStatus.BAD_REQUEST);
        }
        String accessToken;
        try {
            String tokenUri = UriComponentsBuilder.fromUriString(TOKEN_URL)
                    .queryParam("grant_type", "authorization_code")
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret)
                    .queryParam("code", code)
                    .queryParam("state", state)
                    .build().encode().toUriString();
            JsonNode token = restClient.get().uri(tokenUri).retrieve().body(JsonNode.class);
            accessToken = token == null ? null : token.path("access_token").asText(null);
            if (accessToken == null) {
                log.warn("네이버 토큰 응답에 access_token 없음: {}", token == null ? "null" : token.path("error_description").asText(""));
            }
        } catch (Exception e) {
            log.error("네이버 토큰 요청 실패: {}", e.getMessage());
            throw new GoogleIntegrationException("네이버 인증 코드 교환에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
        if (accessToken == null) {
            throw new GoogleIntegrationException("네이버 인증 코드 교환에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
        try {
            JsonNode me = restClient.get().uri(ME_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(JsonNode.class);
            JsonNode r = me == null ? null : me.path("response");
            String id = r == null ? null : r.path("id").asText(null);
            if (id == null || id.isBlank()) {
                throw new GoogleIntegrationException("네이버 계정 정보를 읽지 못했습니다.", HttpStatus.BAD_GATEWAY);
            }
            // 네이버 이메일은 본인 인증을 거친 '연락처 이메일' 이라 확인된 것으로 본다
            return new SocialUserInfo(id, r.path("email").asText(null), true, r.path("name").asText(null));
        } catch (GoogleIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("네이버 사용자 조회 실패: {}", e.getMessage());
            throw new GoogleIntegrationException("네이버 계정 정보를 읽지 못했습니다.", HttpStatus.BAD_GATEWAY);
        }
    }
}
