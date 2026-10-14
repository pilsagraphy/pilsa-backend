package com.back.global.oauth;

import com.back.event.dto.EventCalendarRow;
import com.back.global.oauth.dto.SocialUserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * 카카오 로그인 + 톡캘린더 (카카오 디벨로퍼스 앱 Pilsagraphy). REST 로 직접 호출한다.
 *  - 동의:   https://kauth.kakao.com/oauth/authorize?response_type=code&client_id={REST API 키}&redirect_uri&state[&scope=talk_calendar]
 *  - 토큰:   POST https://kauth.kakao.com/oauth/token (authorization_code | refresh_token). 콘솔에서 클라이언트 시크릿을 켜 둬서 client_secret 필수
 *  - 사용자: GET https://kapi.kakao.com/v2/user/me (Bearer) → id, kakao_account.email / profile.nickname
 *  - 톡캘린더: /v2/api/calendar/create|update|delete/event — 구글처럼 등록·수정·삭제가 다 된다 (2026-10-12)
 * 이메일은 선택 동의라 없을 수 있다. admin-key 는 계정 상태 변경 웹훅(/api/auth/kakao/webhook)의 발신자 확인용.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements SocialOAuthClient {

    private static final String AUTH_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String ME_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String CAL_CREATE_URL = "https://kapi.kakao.com/v2/api/calendar/create/event";
    private static final String CAL_UPDATE_URL = "https://kapi.kakao.com/v2/api/calendar/update/event";
    private static final String CAL_DELETE_URL = "https://kapi.kakao.com/v2/api/calendar/delete/event";
    private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";
    private static final String LOGOUT_URL = "https://kapi.kakao.com/v1/user/logout";
    public static final String CALENDAR_SCOPE = "talk_calendar";
    private static final int TITLE_MAX = 50; // 톡캘린더 제목 상한

    /** 토큰 응답 — refresh_token 은 갱신 때 새 값이 올 수도, 안 올 수도 있다(만료 1달 이내일 때만 새로 준다) */
    public record Tokens(String accessToken, String refreshToken, int expiresIn, String scope) {
    }

    @Value("${kakao.oauth.rest-api-key:}")
    private String restApiKey;

    /** 카카오 콘솔 > 플랫폼 키 > REST API 키 수정 > 클라이언트 시크릿(카카오 로그인) — 켜져 있으면 필수 */
    @Value("${kakao.oauth.client-secret:}")
    private String clientSecret;

    @Value("${kakao.oauth.admin-key:}")
    private String adminKey;

    @Value("${kakao.oauth.login-redirect-uri:https://pilsa.co.kr/api/auth/kakao/callback}")
    private String loginRedirectUri;

    @Value("${kakao.oauth.calendar-redirect-uri:https://pilsa.co.kr/api/user/mypage/calendar/kakao/callback}")
    private String calendarRedirectUri;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public String calendarRedirectUri() {
        return calendarRedirectUri;
    }

    public String adminKey() {
        return adminKey;
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return buildAuthorizeUrl(state, loginRedirectUri, null);
    }

    /** scope 가 있으면 그 동의항목을 추가로 묻는다(톡캘린더). prompt=consent 로 매번 동의 화면을 띄워 refresh token 을 확실히 받는다 */
    public String buildAuthorizeUrl(String state, String redirectUri, String scope) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", restApiKey)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state);
        if (scope != null && !scope.isBlank()) b.queryParam("scope", scope).queryParam("prompt", "consent");
        return b.build().encode().toUriString();
    }

    @Override
    public SocialUserInfo fetchUser(String code, String state) {
        return fetchUser(exchangeTokens(code, loginRedirectUri).accessToken());
    }

    public Tokens exchangeTokens(String code, String redirectUri) {
        if (code == null || code.isBlank()) {
            throw new GoogleIntegrationException("카카오 인증 코드가 없습니다.", HttpStatus.BAD_REQUEST);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) form.add("client_secret", clientSecret);
        return token(form, "카카오 인증 코드 교환에 실패했습니다.");
    }

    public Tokens refreshTokens(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", restApiKey);
        form.add("refresh_token", refreshToken);
        if (clientSecret != null && !clientSecret.isBlank()) form.add("client_secret", clientSecret);
        return token(form, "카카오 토큰 갱신에 실패했습니다. 재연동이 필요할 수 있습니다.");
    }

    public SocialUserInfo fetchUser(String accessToken) {
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

    // ─────────────────────── 톡캘린더 ───────────────────────

    /** 기본 캘린더(primary)에 일정 추가 → event_id */
    public String createEvent(String accessToken, EventCalendarRow row) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("calendar_id", "primary");
        form.add("event", eventJson(row));
        JsonNode res = calendarCall(CAL_CREATE_URL, accessToken, form, "톡캘린더 일정 추가 실패");
        String id = res == null ? null : res.path("event_id").asText(null);
        if (id == null) throw new GoogleIntegrationException("톡캘린더가 일정 id 를 주지 않았습니다.", HttpStatus.BAD_GATEWAY);
        return id;
    }

    public void updateEvent(String accessToken, String eventId, EventCalendarRow row) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("event_id", eventId);
        form.add("calendar_id", "primary");
        form.add("event", eventJson(row));
        calendarCall(CAL_UPDATE_URL, accessToken, form, "톡캘린더 일정 수정 실패");
    }

    public void deleteEvent(String accessToken, String eventId) {
        try {
            String uri = UriComponentsBuilder.fromUriString(CAL_DELETE_URL).queryParam("event_id", eventId).build().encode().toUriString();
            restClient.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).retrieve().toBodilessEntity();
        } catch (Exception e) {
            // 이미 사용자가 지운 일정이면 카카오가 404/400 을 준다 — 목적은 달성된 것이라 삼킨다
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("404") || m.contains("-9011") || m.contains("not found")) return;
            throw new GoogleIntegrationException("톡캘린더 일정 삭제 실패: " + m, HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * 토큰만 만료시킨다(카카오 로그아웃 API) — 앱 연결은 그대로라 같은 카카오 계정의 간편로그인 동의가 살아 있다.
     * 톡캘린더를 끌 때 쓴다: 예전엔 unlink(앱 연결 끊기)를 불러 간편로그인 동의까지 사라질 수 있었다 — 계정 연결과 캘린더는 별개 (PM 2026-10-13). 실패는 삼킨다
     */
    public void expireTokens(String accessToken) {
        try {
            restClient.post().uri(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("카카오 토큰 만료 실패 (무시): {}", e.getMessage());
        }
    }

    /** 카카오 쪽 앱 연결까지 끊는다(동의 전부 회수). 실패는 삼킨다 */
    public void unlink(String accessToken) {
        try {
            restClient.post().uri(UNLINK_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("카카오 연결 끊기 실패 (무시): {}", e.getMessage());
        }
    }

    /**
     * 톡캘린더 event JSON. 시각 일정은 UTC(ICS 와 같은 값), 종일은 날짜 자정 Z 에 all_day=true(카카오 문서 예시 형식),
     * 종료는 배타적(다음날 자정). 제목 50자 제한.
     */
    private String eventJson(EventCalendarRow row) {
        ObjectNode event = objectMapper.createObjectNode();
        String title = row.getTitle() == null ? "필사그래피 일정" : row.getTitle();
        if (title.length() > TITLE_MAX) title = title.substring(0, TITLE_MAX - 1) + "…";
        event.put("title", title);
        ObjectNode time = event.putObject("time");
        boolean allDay = Boolean.TRUE.equals(row.getAllDay());
        if (allDay) {
            time.put("start_at", ymdToIso(row.getStartDate()));
            time.put("end_at", ymdToIso(row.getEndDateExclusive()));
        } else {
            time.put("start_at", utcToIso(row.getStartUtc()));
            time.put("end_at", utcToIso(row.getEndUtc()));
        }
        time.put("time_zone", "Asia/Seoul");
        time.put("all_day", allDay);
        time.put("lunar", false);
        String desc = row.getDescription();
        if (desc != null && !desc.isBlank()) event.put("description", desc.length() > 5000 ? desc.substring(0, 4999) : desc);
        return event.toString();
    }

    private static String ymdToIso(String ymd) { // 20261020 → 2026-10-20T00:00:00Z
        return ymd.substring(0, 4) + "-" + ymd.substring(4, 6) + "-" + ymd.substring(6, 8) + "T00:00:00Z";
    }

    private static String utcToIso(String v) { // 20261020T010000Z → 2026-10-20T01:00:00Z
        return v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6, 8) + "T" + v.substring(9, 11) + ":" + v.substring(11, 13) + ":" + v.substring(13, 15) + "Z";
    }

    private JsonNode calendarCall(String url, String accessToken, MultiValueMap<String, String> form, String errorMessage) {
        try {
            return restClient.post().uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
        } catch (Exception e) {
            throw new GoogleIntegrationException(errorMessage + ": " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private Tokens token(MultiValueMap<String, String> form, String errorMessage) {
        try {
            JsonNode token = restClient.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            String access = token == null ? null : token.path("access_token").asText(null);
            if (access == null) {
                log.warn("카카오 토큰 응답에 access_token 없음: {}", token == null ? "null" : token.path("error_description").asText(""));
                throw new GoogleIntegrationException(errorMessage, HttpStatus.BAD_GATEWAY);
            }
            return new Tokens(access, token.path("refresh_token").asText(null), token.path("expires_in").asInt(21600), token.path("scope").asText(""));
        } catch (GoogleIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("카카오 토큰 요청 실패: {}", e.getMessage());
            throw new GoogleIntegrationException(errorMessage, HttpStatus.BAD_GATEWAY);
        }
    }
}
