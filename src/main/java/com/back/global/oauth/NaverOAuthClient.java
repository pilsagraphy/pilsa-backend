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
 * 네이버 로그인 + 네이버 캘린더 (네이버 개발자센터 > Pilsagraphy). REST 로 직접 호출한다.
 *  - 동의:   https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id&redirect_uri&state
 *  - 토큰:   https://nid.naver.com/oauth2.0/token?grant_type=authorization_code|refresh_token|delete ...
 *  - 사용자: https://openapi.naver.com/v1/nid/me (Bearer) → response.id / email / name
 *  - 캘린더: POST https://openapi.naver.com/calendar/createSchedule.json (calendarId=defaultCalendarId, scheduleIcalString=…)
 *            네이버 캘린더 오픈 API 는 '일정 추가' 하나뿐이다 — 수정·삭제 API 가 없다 (2026-10-12 확인).
 * 제공 정보(이름·이메일·캘린더)는 콘솔에서 정한다. scope 파라미터는 없다.
 * 키가 비어 있으면(아직 properties 에 안 넣음) isConfigured=false 로 버튼이 안내만 한다.
 */
@Slf4j
@Component
public class NaverOAuthClient implements SocialOAuthClient {

    private static final String AUTH_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String ME_URL = "https://openapi.naver.com/v1/nid/me";
    private static final String CREATE_SCHEDULE_URL = "https://openapi.naver.com/calendar/createSchedule.json";

    /** 토큰 응답 — 네이버는 access(1시간)·refresh 를 함께 준다 */
    public record Tokens(String accessToken, String refreshToken, int expiresIn) {
    }

    @Value("${naver.oauth.client-id:}")
    private String clientId;

    @Value("${naver.oauth.client-secret:}")
    private String clientSecret;

    @Value("${naver.oauth.login-redirect-uri:https://pilsa.co.kr/api/auth/naver/callback}")
    private String loginRedirectUri;

    @Value("${naver.oauth.calendar-redirect-uri:https://pilsa.co.kr/api/user/mypage/calendar/naver/callback}")
    private String calendarRedirectUri;

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

    public String calendarRedirectUri() {
        return calendarRedirectUri;
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return buildAuthorizeUrl(state, loginRedirectUri);
    }

    public String buildAuthorizeUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Override
    public SocialUserInfo fetchUser(String code, String state) {
        return fetchUser(exchangeTokens(code, state).accessToken());
    }

    /** 인증 코드 → 토큰. 네이버는 redirect_uri 를 다시 받지 않는다 */
    public Tokens exchangeTokens(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new GoogleIntegrationException("네이버 인증 코드가 없습니다.", HttpStatus.BAD_REQUEST);
        }
        return token(UriComponentsBuilder.fromUriString(TOKEN_URL)
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("code", code)
                .queryParam("state", state), "네이버 인증 코드 교환에 실패했습니다.");
    }

    /** refresh token 으로 access token 재발급 (응답의 refresh_token 은 보통 같은 값) */
    public Tokens refreshTokens(String refreshToken) {
        return token(UriComponentsBuilder.fromUriString(TOKEN_URL)
                .queryParam("grant_type", "refresh_token")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("refresh_token", refreshToken), "네이버 토큰 갱신에 실패했습니다. 재연동이 필요할 수 있습니다.");
    }

    /** 토큰 삭제(연동 해제). 실패해도 우리 쪽 연동은 끊으므로 예외를 삼킨다 */
    public void deleteToken(String accessToken) {
        try {
            String uri = UriComponentsBuilder.fromUriString(TOKEN_URL)
                    .queryParam("grant_type", "delete")
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret)
                    .queryParam("access_token", accessToken)
                    .queryParam("service_provider", "NAVER")
                    .build().encode().toUriString();
            restClient.get().uri(uri).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("네이버 토큰 삭제 실패 (무시하고 로컬 연동만 해제한다): {}", e.getMessage());
        }
    }

    public SocialUserInfo fetchUser(String accessToken) {
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

    /**
     * 기본 캘린더에 일정 하나를 넣는다. ical 은 VCALENDAR 통째(VEVENT 1개). 성공하면 아무것도 반환하지 않는다 —
     * 네이버는 일정 id 를 돌려주지 않고 수정·삭제 API 도 없다. 실패는 예외.
     */
    public void createSchedule(String accessToken, String ical) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("calendarId", "defaultCalendarId");
            form.add("scheduleIcalString", ical);
            JsonNode res = restClient.post().uri(CREATE_SCHEDULE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            String code = res == null ? null : res.path("resultcode").asText(null);
            if (code != null && !"00".equals(code)) {
                throw new GoogleIntegrationException("네이버 캘린더 응답 오류: " + res.path("message").asText(code), HttpStatus.BAD_GATEWAY);
            }
        } catch (GoogleIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new GoogleIntegrationException("네이버 캘린더에 일정을 넣지 못했습니다: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private Tokens token(UriComponentsBuilder builder, String errorMessage) {
        try {
            JsonNode token = restClient.get().uri(builder.build().encode().toUriString()).retrieve().body(JsonNode.class);
            String access = token == null ? null : token.path("access_token").asText(null);
            if (access == null) {
                log.warn("네이버 토큰 응답에 access_token 없음: {}", token == null ? "null" : token.path("error_description").asText(""));
                throw new GoogleIntegrationException(errorMessage, HttpStatus.BAD_GATEWAY);
            }
            return new Tokens(access, token.path("refresh_token").asText(null), token.path("expires_in").asInt(3600));
        } catch (GoogleIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("네이버 토큰 요청 실패: {}", e.getMessage());
            throw new GoogleIntegrationException(errorMessage, HttpStatus.BAD_GATEWAY);
        }
    }
}
