package com.back.global.oauth;

import com.back.global.oauth.dto.SocialUserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로그인 (카카오 디벨로퍼스 앱). REST 로 직접 호출한다.
 *  - 동의:   https://kauth.kakao.com/oauth/authorize?response_type=code&client_id={REST API 키}&redirect_uri&state
 *  - 토큰:   POST https://kauth.kakao.com/oauth/token (grant_type=authorization_code, client_id, redirect_uri, code, client_secret?)
 *  - 사용자: GET https://kapi.kakao.com/v2/user/me (Bearer) → id, kakao_account.email / is_email_verified / profile.nickname
 * 이메일은 선택 동의라 없을 수 있다 — 그러면 '같은 이메일 회원' 안내 없이 "회원가입할까요 / 로그인해서 연결할까요" 만 묻는다.
 * admin-key 는 계정 상태 변경 웹훅(/api/auth/kakao/webhook)의 발신자 확인용.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements SocialOAuthClient {

    private static final String AUTH_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String ME_URL = "https://kapi.kakao.com/v2/user/me";

    @Value("${kakao.oauth.rest-api-key:}")
    private String restApiKey;

    /** 카카오 콘솔 > 보안 > Client Secret 을 '사용함' 으로 켰을 때만 필요 */
    @Value("${kakao.oauth.client-secret:}")
    private String clientSecret;

    @Value("${kakao.oauth.admin-key:}")
    private String adminKey;

    @Value("${kakao.oauth.login-redirect-uri:https://pilsa.co.kr/api/auth/kakao/callback}")
    private String loginRedirectUri;

    private final RestClient restClient = RestClient.create();

    @Override
    public String provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    @Override
    public String loginRedirectUri() {
        return loginRedirectUri;
    }

    public String adminKey() {
        return adminKey;
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", restApiKey)
                .queryParam("redirect_uri", loginRedirectUri)
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Override
    public SocialUserInfo fetchUser(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new GoogleIntegrationException("카카오 인증 코드가 없습니다.", HttpStatus.BAD_REQUEST);
        }
        String accessToken;
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", restApiKey);
            form.add("redirect_uri", loginRedirectUri);
            form.add("code", code);
            if (clientSecret != null && !clientSecret.isBlank()) form.add("client_secret", clientSecret);
            JsonNode token = restClient.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            accessToken = token == null ? null : token.path("access_token").asText(null);
        } catch (Exception e) {
            log.error("카카오 토큰 요청 실패: {}", e.getMessage());
            throw new GoogleIntegrationException("카카오 인증 코드 교환에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
        if (accessToken == null) {
            throw new GoogleIntegrationException("카카오 인증 코드 교환에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
        try {
            JsonNode me = restClient.get().uri(ME_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(JsonNode.class);
            String id = me == null ? null : me.path("id").asText(null);
            if (id == null || id.isBlank()) {
                throw new GoogleIntegrationException("카카오 계정 정보를 읽지 못했습니다.", HttpStatus.BAD_GATEWAY);
            }
            JsonNode account = me.path("kakao_account");
            String email = account.path("email").asText(null);
            boolean verified = email != null && account.path("is_email_valid").asBoolean(false) && account.path("is_email_verified").asBoolean(false);
            // 확인되지 않은 이메일은 식별·안내에 쓰지 않는다 (재할당된 이메일로 남의 계정을 가리킬 수 있다)
            return new SocialUserInfo(id, verified ? email : null, verified, account.path("profile").path("nickname").asText(null));
        } catch (GoogleIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("카카오 사용자 조회 실패: {}", e.getMessage());
            throw new GoogleIntegrationException("카카오 계정 정보를 읽지 못했습니다.", HttpStatus.BAD_GATEWAY);
        }
    }
}
