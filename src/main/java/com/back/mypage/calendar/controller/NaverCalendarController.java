package com.back.mypage.calendar.controller;

import com.back.global.oauth.GoogleProperties;
import com.back.global.oauth.OAuthStateCookie;
import com.back.global.oauth.OAuthStateService;
import com.back.global.security.AuthUtils;
import com.back.mypage.calendar.service.NaverCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 네이버 캘린더 연동 — 구글(GoogleCalendarController)과 같은 모양. 돌아갈 때 쿼리는 `?ncalendar=linked|failed|cancelled`
 * (구글의 `?calendar=` 와 구분). 네이버 API 는 일정 추가만 되므로 '자동 업데이트' 가 아니라 '새 일정 넣기' 다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-네이버캘린더", description = "네이버 캘린더에 새 일정 넣기 (추가만 — 수정·삭제 API 없음)")
public class NaverCalendarController {

    private static final String DEFAULT_RETURN_TO = "/mypage";

    private final GoogleProperties properties;
    private final NaverCalendarService calendarService;
    private final OAuthStateService stateService;
    private final OAuthStateCookie stateCookie;

    @Operation(summary = "네이버 캘린더 - 연동 동의 URL", description = "returnTo(선택, 같은 사이트 안 경로). 키가 없으면 503")
    @GetMapping("/api/user/mypage/calendar/naver/authorize")
    public ResponseEntity<Map<String, String>> authorize(@RequestParam(value = "returnTo", required = false) String returnTo,
                                                         HttpServletResponse response) {
        NaverCalendarService.Authorize authorize =
                calendarService.buildAuthorize(AuthUtils.currentUserId(), GoogleCalendarController.sanitizeReturnTo(returnTo));
        stateCookie.bindState(response, authorize.state());
        return ResponseEntity.ok(Map.of("authorizeUrl", authorize.url()));
    }

    @Operation(summary = "네이버 캘린더 - 연동 콜백 (네이버가 호출)", description = "302 → returnTo?ncalendar=linked|failed|cancelled. 연동 뒤 오늘 이후 일정을 넣는다")
    @GetMapping("/api/user/mypage/calendar/naver/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        boolean sameBrowser = stateCookie.consumeState(request, response, state);
        OAuthStateService.StateData data;
        try {
            data = stateService.consumeData(state, NaverCalendarService.PURPOSE_NAVER_CALENDAR);
        } catch (Exception e) {
            log.info("네이버 캘린더 콜백 state 검증 실패: {}", e.getMessage());
            return redirect(null, "failed");
        }
        String returnTo = data.returnTo();
        if (error != null) {
            log.info("네이버 캘린더 동의 취소 또는 오류: {}", error);
            return redirect(returnTo, "cancelled");
        }
        if (!sameBrowser) {
            log.info("네이버 캘린더 콜백 - state 쿠키 불일치");
            return redirect(returnTo, "failed");
        }
        try {
            Long userId = calendarService.completeLink(code, state, data.userId());
            calendarService.pushUpcomingForUser(userId);
            return redirect(returnTo, "linked");
        } catch (Exception e) {
            log.warn("네이버 캘린더 연동 실패: {}", e.getMessage());
            return redirect(returnTo, "failed");
        }
    }

    @Operation(summary = "네이버 캘린더 - 연동 상태", description = "{linked, naverEmail, lastSentAt, sentCount, configured}")
    @GetMapping("/api/user/mypage/calendar/naver")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(calendarService.getStatus(AuthUtils.currentUserId()));
    }

    @Operation(summary = "네이버 캘린더 - 연동 해제", description = "이미 넣은 일정은 네이버 쪽에서 직접 지워야 한다")
    @DeleteMapping("/api/user/mypage/calendar/naver")
    public ResponseEntity<Map<String, String>> unlink() {
        calendarService.unlink(AuthUtils.currentUserId());
        return ResponseEntity.ok(Map.of("message", "네이버 캘린더 연동을 해제했습니다."));
    }

    private ResponseEntity<Void> redirect(String returnTo, String result) {
        String path = returnTo == null ? DEFAULT_RETURN_TO : returnTo;
        URI uri = UriComponentsBuilder.fromUriString(properties.getFrontendUrl() + path)
                .queryParam("ncalendar", result)
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, uri.toString()).build();
    }
}
