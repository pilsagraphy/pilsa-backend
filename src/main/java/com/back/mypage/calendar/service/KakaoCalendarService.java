package com.back.mypage.calendar.service;

import com.back.event.dto.EventCalendarRow;
import com.back.global.oauth.GoogleIntegrationException;
import com.back.global.oauth.KakaoOAuthClient;
import com.back.global.oauth.OAuthStateService;
import com.back.global.oauth.TokenCipher;
import com.back.global.oauth.dto.SocialUserInfo;
import com.back.mypage.calendar.dto.KakaoCalendarLink;
import com.back.mypage.calendar.dto.UserCalendarEvent;
import com.back.mypage.calendar.mapper.CalendarSyncMapper;
import com.back.mypage.calendar.mapper.KakaoCalendarMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 카카오 톡캘린더 연동 — 구글(GoogleCalendarService + GoogleCalendarSyncService)과 같은 자동 업데이트 (PM 2026-10-12).
 * 톡캘린더 API 는 일정 생성·수정·삭제가 다 되므로 관리자가 일정을 바꾸면 그대로 따라간다.
 * 토큰은 user_kakao_calendar(AES-GCM), 사용자별 일정 매핑은 user_kakao_calendar_events. 실패는 FAILED 로 남겨 10분 배치가 재시도.
 * 카카오 refresh token 은 2달짜리라 갱신 응답에 새 refresh_token 이 오면 갈아끼운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoCalendarService {

    public static final String PURPOSE_KAKAO_CALENDAR = "kakao-calendar";
    public static final int MAX_RETRY = 5;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record Authorize(String url, String state) {
    }

    private final KakaoOAuthClient kakao;
    private final OAuthStateService stateService;
    private final KakaoCalendarMapper mapper;
    private final CalendarSyncMapper eventSource; // findEventForSync · findUpcomingEventsForSync 만 쓴다
    private final TokenCipher tokenCipher;

    public boolean isConfigured() {
        return kakao.isConfigured();
    }

    public Authorize buildAuthorize(Long userId, String returnTo) {
        if (!kakao.isConfigured()) {
            throw new GoogleIntegrationException("카카오 연동은 아직 준비 중이에요.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String state = stateService.issue(PURPOSE_KAKAO_CALENDAR, userId, returnTo);
        return new Authorize(kakao.buildAuthorizeUrl(state, kakao.calendarRedirectUri(), KakaoOAuthClient.CALENDAR_SCOPE), state);
    }

    @Transactional
    public Long completeLink(String code, Long userId) {
        if (userId == null) throw new GoogleIntegrationException("잘못된 인증 요청입니다.", HttpStatus.BAD_REQUEST);
        KakaoOAuthClient.Tokens tokens = kakao.exchangeTokens(code, kakao.calendarRedirectUri());
        if (tokens.scope() == null || !tokens.scope().contains(KakaoOAuthClient.CALENDAR_SCOPE)) {
            throw new GoogleIntegrationException("톡캘린더 권한에 동의해야 연동할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        if (tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
            throw new GoogleIntegrationException("카카오에서 갱신 토큰을 받지 못했습니다. 다시 시도해주세요.", HttpStatus.BAD_GATEWAY);
        }
        SocialUserInfo info = kakao.fetchUser(tokens.accessToken());
        KakaoCalendarLink link = new KakaoCalendarLink();
        link.setUserId(userId);
        link.setKakaoName(info.getEmail() != null ? info.getEmail() : info.getName());
        link.setAccessToken(tokenCipher.encrypt(tokens.accessToken()));
        link.setAccessExpiresAt(LocalDateTime.now().plusSeconds(Math.max(60, tokens.expiresIn() - 60)));
        link.setRefreshToken(tokenCipher.encrypt(tokens.refreshToken()));
        mapper.upsertLink(link);
        log.info("톡캘린더 연동 완료 - userId={}", userId);
        return userId;
    }

    public Map<String, Object> getStatus(Long userId) {
        KakaoCalendarLink link = mapper.findByUserId(userId);
        return Map.of(
                "linked", link != null,
                "kakaoName", link == null || link.getKakaoName() == null ? "" : link.getKakaoName(),
                "lastSyncedAt", link == null || link.getLastSyncedAt() == null ? "" : link.getLastSyncedAt().format(FORMATTER),
                "failedCount", link == null ? 0 : mapper.countExhausted(userId, MAX_RETRY),
                "configured", kakao.isConfigured()
        );
    }

    /** 해제 — removeEvents 면 넣어 둔 일정을 톡캘린더에서 지운다(동기). 매핑 행은 지운다 */
    @Transactional
    public int unlink(Long userId, boolean removeEvents) {
        KakaoCalendarLink link = mapper.findByUserId(userId);
        if (link == null) throw new GoogleIntegrationException("연동된 톡캘린더가 없습니다.", HttpStatus.NOT_FOUND);
        int removed = 0;
        if (removeEvents) removed = removeAllForUser(link);
        try {
            kakao.unlink(accessTokenFor(link));
        } catch (Exception e) {
            log.warn("카카오 토큰 정리 실패 (무시): {}", e.getMessage());
        }
        mapper.deleteMappingsByUserId(userId);
        mapper.deleteByUserId(userId);
        log.info("톡캘린더 연동 해제 - userId={}, 지운 일정={}", userId, removed);
        return removed;
    }

    // ─────────────────────── 팬아웃 ───────────────────────

    @Async
    public void syncAllForUser(Long userId) {
        KakaoCalendarLink link = mapper.findByUserId(userId);
        if (link == null) return;
        List<EventCalendarRow> events = eventSource.findUpcomingEventsForSync();
        log.info("톡캘린더 초기 동기화 - userId={}, 일정={}건", userId, events.size());
        for (EventCalendarRow event : events) syncIfAbsent(link, event);
        mapper.updateLastSyncedAt(userId);
    }

    @Async
    public void onEventCreated(Long eventId) {
        EventCalendarRow event = eventSource.findEventForSync(eventId);
        if (event == null) return;
        List<KakaoCalendarLink> targets = mapper.findAllLinked();
        if (targets.isEmpty()) return;
        log.info("톡캘린더 등록 팬아웃 - eventId={}, 대상={}명", eventId, targets.size());
        for (KakaoCalendarLink link : targets) syncIfAbsent(link, event);
    }

    @Async
    public void onEventUpdated(Long eventId) {
        EventCalendarRow event = eventSource.findEventForSync(eventId);
        if (event == null) return;
        List<KakaoCalendarLink> targets = mapper.findAllLinked();
        if (targets.isEmpty()) return;
        log.info("톡캘린더 수정 팬아웃 - eventId={}, 대상={}명", eventId, targets.size());
        for (KakaoCalendarLink link : targets) syncOne(link, event, mapper.findMapping(link.getUserId(), eventId));
    }

    @Async
    public void onEventDeleted(Long eventId) {
        List<UserCalendarEvent> mappings = mapper.findMappingsByEvent(eventId);
        if (mappings.isEmpty()) return;
        log.info("톡캘린더 삭제 팬아웃 - eventId={}, 대상={}건", eventId, mappings.size());
        for (UserCalendarEvent mapping : mappings) {
            KakaoCalendarLink link = mapper.findByUserId(mapping.getUserId());
            if (link == null) continue;
            try {
                kakao.deleteEvent(accessTokenFor(link), mapping.getGoogleEventId());
                mapper.markDeleted(mapping.getUserId(), eventId);
            } catch (Exception e) {
                log.warn("톡캘린더 삭제 실패 - userId={}, eventId={}, {}", mapping.getUserId(), eventId, e.getMessage());
                mapper.markFailed(mapping.getUserId(), eventId, e.getMessage());
            }
        }
    }

    public int retryFailed(int maxRetry, int limit) {
        List<UserCalendarEvent> targets = mapper.findRetryTargets(maxRetry, limit);
        if (targets.isEmpty()) return 0;
        int recovered = 0;
        for (UserCalendarEvent mapping : targets) {
            KakaoCalendarLink link = mapper.findByUserId(mapping.getUserId());
            if (link == null) {
                mapper.markDeleted(mapping.getUserId(), mapping.getEventId());
                continue;
            }
            EventCalendarRow event = eventSource.findEventForSync(mapping.getEventId());
            if (event == null) {
                removeFromCalendar(link, mapping);
                continue;
            }
            if (syncOne(link, event, mapping)) recovered++;
        }
        log.info("톡캘린더 동기화 재시도 - 대상 {}건, 복구 {}건", targets.size(), recovered);
        return recovered;
    }

    public int removeAllForUser(KakaoCalendarLink link) {
        List<UserCalendarEvent> mappings = mapper.findMappingsByUser(link.getUserId());
        if (mappings.isEmpty()) return 0;
        String accessToken;
        try {
            accessToken = accessTokenFor(link);
        } catch (Exception e) {
            log.warn("톡캘린더 해제 중 토큰 실패 - userId={}, {}", link.getUserId(), e.getMessage());
            return 0;
        }
        int removed = 0;
        for (UserCalendarEvent mapping : mappings) {
            if (mapping.getGoogleEventId() == null) continue;
            try {
                kakao.deleteEvent(accessToken, mapping.getGoogleEventId());
                mapper.markDeleted(link.getUserId(), mapping.getEventId());
                removed++;
            } catch (Exception e) {
                log.warn("톡캘린더 해제 중 일정 삭제 실패 - userId={}, eventId={}, {}", link.getUserId(), mapping.getEventId(), e.getMessage());
            }
        }
        return removed;
    }

    // ─────────────────────── 내부 ───────────────────────

    private void syncIfAbsent(KakaoCalendarLink link, EventCalendarRow event) {
        UserCalendarEvent existing = mapper.findMapping(link.getUserId(), event.getEventId());
        if (existing != null && UserCalendarEvent.SYNCED.equals(existing.getSyncStatus())) return;
        syncOne(link, event, existing);
    }

    private boolean syncOne(KakaoCalendarLink link, EventCalendarRow event, UserCalendarEvent mapping) {
        Long userId = link.getUserId();
        Long eventId = event.getEventId();
        String kakaoEventId = mapping == null ? null : mapping.getGoogleEventId();
        if (kakaoEventId == null) {
            UserCalendarEvent pending = new UserCalendarEvent();
            pending.setUserId(userId);
            pending.setEventId(eventId);
            pending.setSyncStatus(UserCalendarEvent.PENDING);
            pending.setRetryCount(mapping == null ? 0 : mapping.getRetryCount());
            mapper.upsertMapping(pending);
        }
        try {
            String accessToken = accessTokenFor(link);
            if (kakaoEventId == null) kakaoEventId = kakao.createEvent(accessToken, event);
            else kakao.updateEvent(accessToken, kakaoEventId, event);
            mapper.markSynced(userId, eventId, kakaoEventId);
            return true;
        } catch (Exception e) {
            log.warn("톡캘린더 동기화 실패 - userId={}, eventId={}, {}", userId, eventId, e.getMessage());
            mapper.markFailed(userId, eventId, e.getMessage());
            return false;
        }
    }

    private void removeFromCalendar(KakaoCalendarLink link, UserCalendarEvent mapping) {
        if (mapping.getGoogleEventId() == null) {
            mapper.markDeleted(mapping.getUserId(), mapping.getEventId());
            return;
        }
        try {
            kakao.deleteEvent(accessTokenFor(link), mapping.getGoogleEventId());
            mapper.markDeleted(mapping.getUserId(), mapping.getEventId());
        } catch (Exception e) {
            mapper.markFailed(mapping.getUserId(), mapping.getEventId(), e.getMessage());
        }
    }

    /** 만료 전이면 저장된 access token, 아니면 refresh 로 새로 받아 저장(새 refresh 가 오면 그것도) */
    private String accessTokenFor(KakaoCalendarLink link) {
        if (link.getAccessExpiresAt() != null && link.getAccessExpiresAt().isAfter(LocalDateTime.now())) {
            return tokenCipher.decrypt(link.getAccessToken());
        }
        KakaoOAuthClient.Tokens t = kakao.refreshTokens(tokenCipher.decrypt(link.getRefreshToken()));
        byte[] newRefresh = t.refreshToken() == null || t.refreshToken().isBlank() ? null : tokenCipher.encrypt(t.refreshToken());
        mapper.updateTokens(link.getUserId(), tokenCipher.encrypt(t.accessToken()), Math.max(60, t.expiresIn() - 60), newRefresh);
        link.setAccessExpiresAt(LocalDateTime.now().plusSeconds(Math.max(60, t.expiresIn() - 60)));
        link.setAccessToken(tokenCipher.encrypt(t.accessToken()));
        return t.accessToken();
    }
}
