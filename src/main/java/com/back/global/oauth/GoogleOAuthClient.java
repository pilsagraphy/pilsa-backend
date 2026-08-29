package com.back.global.oauth;

import com.back.global.oauth.dto.GoogleTokenResponse;
import com.back.global.oauth.dto.GoogleUserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 구글 OAuth 2.0 클라이언트.
 *
 * google-api-client 라이브러리를 쓰지 않고 REST 로 직접 호출한다 —
 * 필요한 건 토큰 교환/갱신/폐기 세 가지뿐이라 의존성을 늘릴 이유가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

    private final GoogleProperties properties;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.create();

    /**
     * 동의 화면 URL 을 만든다.
     *
     * @param offline true 면 refresh token 을 받는다(캘린더 연동용).
     *                구글은 이미 동의한 사용자에게는 refresh token 을 다시 주지 않으므로
     *                prompt=consent 까지 같이 붙여야 재연동 시에도 확실히 받는다.
     */
    public String buildAuthorizeUrl(String scope, String redirectUri, String state, boolean offline) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("state", state)
                .queryParam("include_granted_scopes", "true");

        if (offline) {
            builder.queryParam("access_type", "offline")
                   .queryParam("prompt", "consent");
        }
        return builder.build().encode().toUriString();
    }

    /** authorization code 를 토큰으로 교환한다. */
    public GoogleTokenResponse exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        return postForToken(form, "구글 인증 코드 교환에 실패했습니다.");
    }

    /**
     * refresh token 으로 access token 을 재발급한다.
     * 응답에는 refresh_token 이 들어 있지 않다 — 기존 것을 계속 쓴다.
     */
    public GoogleTokenResponse refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("grant_type", "refresh_token");

        return postForToken(form, "구글 액세스 토큰 갱신에 실패했습니다. 재연동이 필요할 수 있습니다.");
    }

    /**
     * 토큰을 폐기한다. 연동 해제 시 호출.
     * 이미 만료·폐기된 토큰이면 400 이 오는데, 결과적으로 목적은 달성된 것이므로 실패로 보지 않는다.
     */
    public void revoke(String token) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", token);

            restClient.post()
                    .uri(REVOKE_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("구글 토큰 폐기 실패 (무시하고 로컬 연동만 해제한다): {}", e.getMessage());
        }
    }

    /**
     * id_token 에서 사용자 식별 정보를 꺼낸다.
     *
     * 서명 검증을 하지 않는 이유: 이 토큰은 브라우저를 거치지 않고 서버가 구글 토큰 엔드포인트에서
     * HTTPS 로 직접 받아온 것이라 중간에 변조될 수 없다. 구글도 이 경우 검증을 생략해도 된다고 안내한다.
     * (브라우저에서 받은 id_token 을 서버로 전달받는 방식이라면 반드시 서명을 검증해야 한다.)
     */
    public GoogleUserInfo parseIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new GoogleIntegrationException("구글 응답에 id_token 이 없습니다.", HttpStatus.BAD_GATEWAY);
        }
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new GoogleIntegrationException("구글 id_token 형식이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY);
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(payload);

            GoogleUserInfo info = new GoogleUserInfo();
            info.setSub(node.path("sub").asText(null));
            info.setEmail(node.path("email").asText(null));
            info.setEmailVerified(node.path("email_verified").asBoolean(false));
            info.setName(node.path("name").asText(null));

            if (info.getSub() == null || info.getEmail() == null) {
                throw new GoogleIntegrationException("구글 계정 정보를 읽지 못했습니다.", HttpStatus.BAD_GATEWAY);
            }
            return info;
        } catch (GoogleIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new GoogleIntegrationException("구글 id_token 파싱에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
    }

    private GoogleTokenResponse postForToken(MultiValueMap<String, String> form, String errorMessage) {
        try {
            return restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (Exception e) {
            log.error("구글 토큰 요청 실패: {}", e.getMessage());
            throw new GoogleIntegrationException(errorMessage, HttpStatus.BAD_GATEWAY);
        }
    }
}
