package com.back.mypage.calendar.controller;

import com.back.global.oauth.GoogleProperties;
import com.back.global.oauth.OAuthStateCookie;
import com.back.global.oauth.OAuthStateService;
import com.back.global.security.AuthUtils;
import com.back.mypage.calendar.dto.CalendarLinkStatusResponse;
import com.back.mypage.calendar.service.GoogleCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 마이페이지 - 구글 캘린더 연동.
 *
 * 왜 ICS 구독이 아니라 이 방식인가:
 * Google Calendar API 에는 외부 ICS URL 을 구독으로 추가하는 메서드가 없다.
 * 안드로이드 구글 캘린더 앱에도 URL 구독 기능이 없어서, 안드로이드 사용자는 PC 로 가지 않는 한
 * `/api/event/calendar.ics` 를 구독할 방법이 없었다.
 * 그래서 사용자 동의를 받아 서버가 각자 캘린더에 일정을 직접 넣는다 — 사용자에게는 구독처럼 보인다.
 *
 * 연동은 마이페이지(정보 수정)에서도, 캘린더 페이지의 [내 캘린더에 구독]에서도 시작할 수 있다.
 * 어디서 시작했든 동의가 끝나면 그 화면으로 돌아가야 하므로 authorize 가 returnTo 를 받아 state 에 담는다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-구글캘린더")
public class GoogleCalendarController {

    /** returnTo 를 주지 않은 연동(마이페이지 토글)이 돌아갈 곳 */
    private static final String DEFAULT_RETURN_TO = "/mypage";

    private final GoogleProperties properties;
    private final GoogleCalendarService calendarService;
    private final OAuthStateService stateService;
    private final OAuthStateCookie stateCookie;

    @Operation(summary = "구글 캘린더 - 연동 동의 URL 발급",
            description = """
                    마이페이지의 [내 구글 캘린더에 자동 등록] 토글, 또는 캘린더 페이지의 [내 캘린더에 구독]이 호출한다.
                    받은 URL 로 이동시키면 구글 동의 후 `{프론트}{returnTo}?calendar=linked` 로 돌아온다.

                    - `returnTo` (선택): 돌아갈 프론트 경로. 같은 사이트 안 경로(`/...`)만 받고 그 외는 무시한다.
                      생략하면 `/mypage`. 캘린더 페이지는 `/calendar` 를 넘겨 그 자리에서 이어서 안내한다.

                    ### 응답 예시
                    ```json
                    {"authorizeUrl":"https://accounts.google.com/o/oauth2/v2/auth?...&access_type=offline&prompt=consent"}
                    ```
                    로그인 스코프와 분리해 요청한다 — 로그인만 하려는 사용자에게 캘린더 권한을 물으면 이탈한다.
                    state 는 5분간만 유효하고, 같은 값을 HttpOnly 쿠키로도 심어 콜백이 이 브라우저로 돌아온 것인지 대조한다.""")
    @GetMapping("/api/user/mypage/calendar/google/authorize")
    public ResponseEntity<Map<String, String>> authorize(
            @Parameter(description = "동의 후 돌아갈 프론트 경로 (같은 사이트 안 경로만)", example = "/calendar")
            @RequestParam(value = "returnTo", required = false) String returnTo,
            HttpServletResponse response) {
        GoogleCalendarService.Authorize authorize =
                calendarService.buildAuthorize(AuthUtils.currentUserId(), sanitizeReturnTo(returnTo));
        stateCookie.bindState(response, authorize.state());
        return ResponseEntity.ok(Map.of("authorizeUrl", authorize.url()));
    }

    @Operation(summary = "구글 캘린더 - 연동 콜백 (구글이 호출)",
            description = """
                    구글이 브라우저를 이 주소로 돌려보낸다. **프론트가 직접 호출하는 API 가 아니다.**

                    이 콜백에는 Authorization 헤더가 없다(구글이 리다이렉트로 부른다).
                    따라서 사용자 식별은 state 로 하며, SecurityConfig 에서 permitAll 로 열려 있다.

                    처리 후 authorize 때 받은 `returnTo`(없으면 `/mypage`)로 302:
                    `?calendar=linked` (성공) · `?calendar=failed` (실패, state 만료·쿠키 불일치 포함) · `?calendar=cancelled` (사용자 취소).
                    state 자체를 읽을 수 없으면 돌아갈 곳도 모르므로 `/mypage?calendar=failed`.
                    연동이 끝나면 아직 지나지 않은 일정을 백그라운드로 캘린더에 채워 넣는다.""")
    @GetMapping("/api/user/mypage/calendar/google/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        // 이 브라우저가 시작한 흐름인지 — authorize 때 심은 쿠키와 쿼리의 state 가 같아야 한다 (검사 후 쿠키는 지운다)
        boolean sameBrowser = stateCookie.consumeState(request, response, state);

        // 취소·실패라도 시작한 화면으로 돌려보내야 하므로 state 를 먼저 소비해 returnTo 를 꺼낸다
        OAuthStateService.StateData data;
        try {
            data = stateService.consumeData(state, OAuthStateService.PURPOSE_CALENDAR);
        } catch (Exception e) {
            log.info("구글 캘린더 콜백 state 검증 실패: {}", e.getMessage());
            return redirect(null, "failed");
        }
        String returnTo = data.returnTo();

        if (error != null) {
            log.info("구글 캘린더 동의 취소 또는 오류: {}", error);
            return redirect(returnTo, "cancelled");
        }
        if (!sameBrowser) {
            log.info("구글 캘린더 콜백 - state 쿠키 불일치 (다른 브라우저에서 시작된 흐름이거나 쿠키 만료)");
            return redirect(returnTo, "failed");
        }

        try {
            Long userId = calendarService.completeLink(code, data.userId());
            // 초기 동기화는 연동 저장이 커밋된 뒤에 시작해야 방금 저장한 토큰을 읽을 수 있다
            calendarService.startInitialSync(userId);
            return redirect(returnTo, "linked");
        } catch (Exception e) {
            log.warn("구글 캘린더 연동 실패: {}", e.getMessage());
            return redirect(returnTo, "failed");
        }
    }

    @Operation(summary = "구글 캘린더 - 연동 상태 조회",
            description = """
                    마이페이지 토글 상태를 그릴 때 호출한다.

                    ### 응답 예시
                    ```json
                    {"linked":true,"googleEmail":"hong@gmail.com","lastSyncedAt":"2026-08-23 14:05:00",
                     "syncedCount":12,"failedCount":0}
                    ```
                    `failedCount` 가 0 보다 크면 토큰이 죽었거나 사용자가 구글 계정 설정에서 권한을 회수한 것이다 —
                    토글은 켜져 있는데 일정이 안 들어가는 상태이므로 재연동을 안내해야 한다.""")
    @GetMapping("/api/user/mypage/calendar/google")
    public ResponseEntity<CalendarLinkStatusResponse> status() {
        return ResponseEntity.ok(calendarService.getStatus(AuthUtils.currentUserId()));
    }

    @Operation(summary = "구글 캘린더 - 연동 해제",
            description = """
                    연동을 끊고 구글 토큰을 폐기한다.

                    `removeEvents=true` 면 그동안 넣어둔 일정을 사용자 캘린더에서 지운다.
                    기본값은 false — 사용자가 이미 그 일정을 보고 일정을 잡았을 수 있어 말없이 지우면 안 된다.
                    프론트는 해제 시 "등록된 일정도 함께 삭제할까요?" 를 물어보고 이 값을 정한다.

                    ### 응답 예시
                    ```json
                    {"message":"구글 캘린더 연동을 해제했습니다.","removedEvents":12}
                    ```""")
    @DeleteMapping("/api/user/mypage/calendar/google")
    public ResponseEntity<Map<String, Object>> unlink(
            @Parameter(description = "넣어둔 일정도 캘린더에서 지울지 여부", example = "false")
            @RequestParam(value = "removeEvents", defaultValue = "false") boolean removeEvents) {

        int removed = calendarService.unlink(AuthUtils.currentUserId(), removeEvents);
        return ResponseEntity.ok(Map.of(
                "message", "구글 캘린더 연동을 해제했습니다.",
                "removedEvents", removed
        ));
    }

    /**
     * returnTo 는 같은 사이트 안 경로('/...')만 받는다 — 외부 주소·프로토콜 상대 주소('//evil')를 넣어
     * 동의 뒤 다른 사이트로 보내는 열린 리다이렉트를 막는다. 조건에 안 맞으면 기본 화면으로 간다.
     */
    static String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return null;
        }
        boolean relativePath = returnTo.startsWith("/") && !returnTo.startsWith("//") && !returnTo.startsWith("/\\");
        boolean printable = returnTo.chars().noneMatch(c -> c < 0x20 || c == 0x7f);
        if (!relativePath || !printable || returnTo.length() > 200) {
            return null;
        }
        return returnTo;
    }

    private ResponseEntity<Void> redirect(String returnTo, String result) {
        String path = returnTo == null ? DEFAULT_RETURN_TO : returnTo;
        URI uri = UriComponentsBuilder.fromUriString(properties.getFrontendUrl() + path)
                .queryParam("calendar", result)
                .build().encode().toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toString())
                .build();
    }
}
