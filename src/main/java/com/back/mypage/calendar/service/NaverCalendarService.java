package com.back.mypage.calendar.service;

import com.back.event.dto.EventCalendarRow;
import com.back.event.mapper.EventMapper;
import com.back.event.service.EventService;
import com.back.global.oauth.GoogleIntegrationException;
import com.back.global.oauth.NaverOAuthClient;
import com.back.global.oauth.OAuthStateService;
import com.back.global.oauth.TokenCipher;
import com.back.global.oauth.dto.SocialUserInfo;
import com.back.mypage.calendar.dto.NaverCalendarLink;
import com.back.mypage.calendar.mapper.NaverCalendarMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 네이버 캘린더 '새 일정 넣기' (PM 2026-10-12 "추가만이라도").
 *
 * 네이버 캘린더 오픈 API 는 일정 추가(createSchedule) 하나뿐이라 구글처럼 수정·삭제를 따라가지 못한다.
 * 그래서 연동하면 (1) 아직 지나지 않은 일정을 한 번 넣어 주고, (2) 이후 관리자가 새 일정을 등록할 때마다 넣어 준다.
 * 관리자가 일정을 고치거나 지우면 사용자 네이버 캘린더에는 반영되지 않는다 — 화면에서 그렇게 안내한다.
 * 토큰은 user_naver_calendar 에 AES-GCM 으로 저장. access 는 1시간이라 만료 전이면 그대로, 아니면 refresh 로 재발급.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverCalendarService {

    public static final String PURPOSE_NAVER_CALENDAR = "naver-calendar";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public record Authorize(String url, String state) {
    }

    private final NaverOAuthClient naver;
    private final OAuthStateService stateService;
    private final NaverCalendarMapper mapper;
    private final EventMapper eventMapper;
    private final EventService eventService;
    private final TokenCipher tokenCipher;

    public boolean isConfigured() {
        return naver.isConfigured();
    }

    public Authorize buildAuthorize(Long userId, String returnTo) {
        if (!naver.isConfigured()) {
            throw new GoogleIntegrationException("네이버 연동은 아직 준비 중이에요.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String state = stateService.issue(PURPOSE_NAVER_CALENDAR, userId, returnTo);
        return new Authorize(naver.buildAuthorizeUrl(state, naver.calendarRedirectUri()), state);
    }

    /** 콜백 — 토큰을 받아 저장한다. 사용자는 state 로 식별(컨트롤러가 검증). */
    @Transactional
    public Long completeLink(String code, String state, Long userId) {
        if (userId == null) throw new GoogleIntegrationException("잘못된 인증 요청입니다.", HttpStatus.BAD_REQUEST);
        NaverOAuthClient.Tokens tokens = naver.exchangeTokens(code, state);
        if (tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
            throw new GoogleIntegrationException("네이버에서 갱신 토큰을 받지 못했습니다. 다시 시도해주세요.", HttpStatus.BAD_GATEWAY);
        }
        SocialUserInfo info = naver.fetchUser(tokens.accessToken());
        NaverCalendarLink link = new NaverCalendarLink();
        link.setUserId(userId);
        link.setNaverEmail(info.getEmail());
        link.setAccessToken(tokenCipher.encrypt(tokens.accessToken()));
        link.setAccessExpiresAt(LocalDateTime.now().plusSeconds(Math.max(60, tokens.expiresIn() - 60)));
        link.setRefreshToken(tokenCipher.encrypt(tokens.refreshToken()));
        mapper.upsertLink(link);
        log.info("네이버 캘린더 연동 완료 - userId={}", userId);
        return userId;
    }

    public Map<String, Object> getStatus(Long userId) {
        NaverCalendarLink link = mapper.findByUserId(userId);
        return Map.of(
                "linked", link != null,
                "naverEmail", link == null || link.getNaverEmail() == null ? "" : link.getNaverEmail(),
                "lastSentAt", link == null || link.getLastSentAt() == null ? "" : link.getLastSentAt().format(FORMATTER),
                "sentCount", link == null ? 0 : link.getSentCount(),
                "configured", naver.isConfigured()
        );
    }

    /** 해제 — 네이버 토큰 삭제(실패 무시) + 우리 행 삭제. 이미 넣은 일정은 네이버에서 지울 수 없어 그대로 남는다 */
    @Transactional
    public void unlink(Long userId) {
        NaverCalendarLink link = mapper.findByUserId(userId);
        if (link == null) throw new GoogleIntegrationException("연동된 네이버 캘린더가 없습니다.", HttpStatus.NOT_FOUND);
        try {
            naver.deleteToken(tokenCipher.decrypt(link.getAccessToken()));
        } catch (Exception e) {
            log.warn("네이버 토큰 복호화 실패 (무시): {}", e.getMessage());
        }
        mapper.deleteByUserId(userId);
        log.info("네이버 캘린더 연동 해제 - userId={}", userId);
    }

    /** 연동 직후 — 오늘 이후 일정을 한 번 넣는다 (커밋 뒤 호출) */
    @Async
    public void pushUpcomingForUser(Long userId) {
        NaverCalendarLink link = mapper.findByUserId(userId);
        if (link == null) return;
        String today = LocalDate.now().format(YMD);
        List<EventCalendarRow> upcoming = eventMapper.findAllForCalendar().stream()
                .filter(r -> r.getStartDate() != null && r.getStartDate().compareTo(today) >= 0)
                .toList();
        int sent = 0;
        String accessToken = accessTokenFor(link);
        if (accessToken == null) return;
        for (EventCalendarRow row : upcoming) {
            try {
                naver.createSchedule(accessToken, eventService.buildEventFeed(row.getEventId()));
                sent++;
            } catch (Exception e) {
                log.warn("네이버 캘린더 초기 넣기 실패 - userId={}, eventId={}, {}", userId, row.getEventId(), e.getMessage());
            }
        }
        if (sent > 0) mapper.bumpSent(userId, sent);
        log.info("네이버 캘린더 초기 넣기 - userId={}, 대상 {}건 중 {}건", userId, upcoming.size(), sent);
    }

    /** 관리자가 새 일정을 등록했을 때 — 연동한 모든 회원의 네이버 캘린더에 넣는다 (수정·삭제는 반영 불가) */
    @Async
    public void onEventCreated(Long eventId) {
        List<NaverCalendarLink> links = mapper.findAllLinked();
        if (links.isEmpty()) return;
        String ical;
        try {
            ical = eventService.buildEventFeed(eventId);
        } catch (Exception e) {
            log.warn("네이버 캘린더 팬아웃 - 일정 {} 을 읽지 못함: {}", eventId, e.getMessage());
            return;
        }
        int ok = 0;
        for (NaverCalendarLink link : links) {
            try {
                String accessToken = accessTokenFor(link);
                if (accessToken == null) continue;
                naver.createSchedule(accessToken, ical);
                mapper.bumpSent(link.getUserId(), 1);
                ok++;
            } catch (Exception e) {
                log.warn("네이버 캘린더 넣기 실패 - userId={}, eventId={}, {}", link.getUserId(), eventId, e.getMessage());
            }
        }
        log.info("네이버 캘린더 팬아웃 - eventId={}, 대상 {}명 중 {}명", eventId, links.size(), ok);
    }

    /** 만료 전이면 저장된 access token, 아니면 refresh 로 새로 받아 저장. 갱신까지 실패하면 null */
    private String accessTokenFor(NaverCalendarLink link) {
        try {
            if (link.getAccessExpiresAt() != null && link.getAccessExpiresAt().isAfter(LocalDateTime.now())) {
                return tokenCipher.decrypt(link.getAccessToken());
            }
            NaverOAuthClient.Tokens t = naver.refreshTokens(tokenCipher.decrypt(link.getRefreshToken()));
            mapper.updateAccessToken(link.getUserId(), tokenCipher.encrypt(t.accessToken()), Math.max(60, t.expiresIn() - 60));
            return t.accessToken();
        } catch (Exception e) {
            log.warn("네이버 토큰 갱신 실패 - userId={}, {} (재연동 필요)", link.getUserId(), e.getMessage());
            return null;
        }
    }
}
