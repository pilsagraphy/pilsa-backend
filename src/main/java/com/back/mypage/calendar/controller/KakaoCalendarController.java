package com.back.mypage.calendar.controller;

import com.back.global.oauth.GoogleProperties;
import com.back.global.oauth.OAuthStateCookie;
import com.back.global.oauth.OAuthStateService;
import com.back.global.security.AuthUtils;
import com.back.mypage.calendar.service.KakaoCalendarService;
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

/** 카카오 톡캘린더 연동 — 구글과 같은 모양. 돌아갈 때 쿼리는 `?kcalendar=linked|failed|cancelled` */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-톡캘린더", description = "카카오 톡캘린더 자동 등록 (등록·수정·삭제 반영)")
public class KakaoCalendarController {

    private static final String DEFAULT_RETURN_TO = "/mypage";

    private final GoogleProperties properties;
    private final KakaoCalendarService calendarService;
    private final OAuthStateService stateService;
    private final OAuthStateCookie stateCookie;

    @Operation(summary = "톡캘린더 - 연동 동의 URL", description = "returnTo(선택). scope=talk_calendar 동의를 받는다. 키가 없으면 503")
    @GetMapping("/api/user/mypage/calendar/kakao/authorize")
    public ResponseEntity<Map<String, String>> authorize(@RequestParam(value = "returnTo", required = false) String returnTo,
                                                         HttpServletResponse response) {
        KakaoCalendarService.Authorize authorize =
                calendarService.buildAuthorize(AuthUtils.currentUserId(), GoogleCalendarController.sanitizeReturnTo(returnTo));
        stateCookie.bindState(response, authorize.state());
        return ResponseEntity.ok(Map.of("authorizeUrl", authorize.url()));
    }

    @Operation(summary = "톡캘린더 - 연동 콜백 (카카오가 호출)", description = "302 → returnTo?kcalendar=linked|failed|cancelled. 연동 뒤 초기 동기화")
    @GetMapping("/api/user/mypage/calendar/kakao/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        boolean sameBrowser = stateCookie.consumeState(request, response, state);
        OAuthStateService.StateData data;
        try {
            data = stateService.consumeData(state, KakaoCalendarService.PURPOSE_KAKAO_CALENDAR);
        } catch (Exception e) {
            log.info("톡캘린더 콜백 state 검증 실패: {}", e.getMessage());
            return redirect(null, "failed");
        }
        String returnTo = data.returnTo();
        if (error != null) {
            log.info("톡캘린더 동의 취소 또는 오류: {}", error);
            return redirect(returnTo, "cancelled");
        }
        if (!sameBrowser) {
            log.info("톡캘린더 콜백 - state 쿠키 불일치");
            return redirect(returnTo, "failed");
        }
        try {
            Long userId = calendarService.completeLink(code, data.userId());
            calendarService.syncAllForUser(userId);
            return redirect(returnTo, "linked");
        } catch (Exception e) {
            log.warn("톡캘린더 연동 실패: {}", e.getMessage());
            return redirect(returnTo, "failed");
        }
    }

    @Operation(summary = "톡캘린더 - 연동 상태", description = "{linked, kakaoName, lastSyncedAt, failedCount, configured}")
    @GetMapping("/api/user/mypage/calendar/kakao")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(calendarService.getStatus(AuthUtils.currentUserId()));
    }

    @Operation(summary = "톡캘린더 - 연동 해제", description = "removeEvents=true 면 넣어 둔 일정도 지운다. 응답 { message, removedEvents }")
    @DeleteMapping("/api/user/mypage/calendar/kakao")
    public ResponseEntity<Map<String, Object>> unlink(@RequestParam(value = "removeEvents", defaultValue = "false") boolean removeEvents) {
        int removed = calendarService.unlink(AuthUtils.currentUserId(), removeEvents);
        return ResponseEntity.ok(Map.of("message", "톡캘린더 연동을 해제했습니다.", "removedEvents", removed));
    }

    private ResponseEntity<Void> redirect(String returnTo, String result) {
        String path = returnTo == null ? DEFAULT_RETURN_TO : returnTo;
        URI uri = UriComponentsBuilder.fromUriString(properties.getFrontendUrl() + path)
                .queryParam("kcalendar", result)
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, uri.toString()).build();
    }
}
