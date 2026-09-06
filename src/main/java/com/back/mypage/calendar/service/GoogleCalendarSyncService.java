package com.back.mypage.calendar.service;

import com.back.event.dto.EventCalendarRow;
import com.back.global.oauth.GoogleOAuthClient;
import com.back.global.oauth.TokenCipher;
import com.back.mypage.calendar.dto.GoogleCalendarLink;
import com.back.mypage.calendar.dto.UserCalendarEvent;
import com.back.mypage.calendar.mapper.CalendarSyncMapper;
import com.back.mypage.calendar.mapper.GoogleCalendarMapper;
import com.back.mypage.calendar.support.GoogleCalendarClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 일정 변경을 연동 사용자들의 구글 캘린더에 반영한다 (팬아웃).
 *
 * 전부 @Async 다. 관리자가 일정을 하나 등록할 때마다 연동 사용자 수만큼 구글 API 를 호출하는데,
 * 이걸 동기로 돌리면 관리자 화면이 그 시간만큼 멈추고 구글이 느려지면 등록 자체가 실패한다.
 * 일정 등록은 우리 DB 에 저장된 시점에 이미 성공이고, 캘린더 반영은 뒤따라가는 부수 작업이다.
 *
 * 실패는 user_calendar_events.sync_status='FAILED' 로 남긴다 —
 * 마이페이지 연동 상태 조회가 이 값을 세어 사용자에게 재연동을 안내한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarSyncService {

    /**
     * 자동 복구를 포기하는 시도 횟수.
     *
     * 재시도 배치와 연동 상태 조회가 같은 값을 봐야 한다 — 배치가 아직 재시도할 건을
     * 상태 조회가 "실패"로 세면 곧 복구될 일에 재연동을 안내하게 된다.
     */
    public static final int MAX_RETRY = 5;

    private final GoogleCalendarMapper calendarMapper;
    private final CalendarSyncMapper syncMapper;
    private final GoogleOAuthClient oauthClient;
    private final GoogleCalendarClient calendarClient;
    private final TokenCipher tokenCipher;

    // ─────────────────────── 일정 변경 팬아웃 ───────────────────────

    /** 일정 신규 등록 → 연동 사용자 전원 캘린더에 추가. */
    @Async
    public void onEventCreated(Long eventId) {
        EventCalendarRow event = syncMapper.findEventForSync(eventId);
        if (event == null) {
            log.warn("동기화할 일정을 찾지 못했습니다 - eventId={}", eventId);
            return;
        }

        List<GoogleCalendarLink> targets = calendarMapper.findAllLinked();
        log.info("일정 등록 팬아웃 시작 - eventId={}, 대상={}명", eventId, targets.size());

        for (GoogleCalendarLink link : targets) {
            syncIfAbsent(link, event);
        }
    }

    /**
     * 일정 수정 → 이미 넣어둔 이벤트를 갱신.
     * 연동한 뒤에 등록된 일정이라 매핑이 없는 사용자에게는 새로 넣는다.
     */
    @Async
    public void onEventUpdated(Long eventId) {
        EventCalendarRow event = syncMapper.findEventForSync(eventId);
        if (event == null) {
            log.warn("동기화할 일정을 찾지 못했습니다 - eventId={}", eventId);
            return;
        }

        List<GoogleCalendarLink> targets = calendarMapper.findAllLinked();
        log.info("일정 수정 팬아웃 시작 - eventId={}, 대상={}명", eventId, targets.size());

        for (GoogleCalendarLink link : targets) {
            // 수정은 이미 반영된 사용자도 다시 밀어야 하므로 SYNCED 를 걸러내지 않는다
            syncOne(link, event, syncMapper.findMapping(link.getUserId(), eventId));
        }
    }

    /**
     * 일정 삭제 → 사용자 캘린더에서도 제거.
     *
     * 삭제는 events 행이 지워지기 전에 호출돼도 되고 후에 호출돼도 된다 —
     * 필요한 건 매핑 테이블의 google_event_id 뿐이라 일정 본문을 조회하지 않는다.
     */
    @Async
    public void onEventDeleted(Long eventId) {
        List<UserCalendarEvent> mappings = syncMapper.findMappingsByEvent(eventId);
        log.info("일정 삭제 팬아웃 시작 - eventId={}, 대상={}건", eventId, mappings.size());

        for (UserCalendarEvent mapping : mappings) {
            GoogleCalendarLink link = calendarMapper.findByUserId(mapping.getUserId());
            if (link == null) {
                continue; // 이미 연동을 해제한 사용자
            }

            try {
                String accessToken = accessTokenOf(link);
                calendarClient.deleteEvent(accessToken, link.calendarIdOrDefault(), mapping.getGoogleEventId());
                syncMapper.markDeleted(mapping.getUserId(), eventId);
            } catch (Exception e) {
                log.warn("일정 삭제 동기화 실패 - userId={}, eventId={}, {}", mapping.getUserId(), eventId, e.getMessage());
                syncMapper.markFailed(mapping.getUserId(), eventId, e.getMessage());
            }
        }
    }

    // ─────────────────────── 사용자 단위 ───────────────────────

    /** 연동 직후 초기 동기화 — 아직 안 끝난 일정만 넣는다. */
    @Async
    public void syncAllForUser(Long userId) {
        GoogleCalendarLink link = calendarMapper.findByUserId(userId);
        if (link == null) {
            return;
        }

        List<EventCalendarRow> events = syncMapper.findUpcomingEventsForSync();
        log.info("초기 동기화 시작 - userId={}, 일정={}건", userId, events.size());

        for (EventCalendarRow event : events) {
            syncIfAbsent(link, event);
        }
        calendarMapper.updateLastSyncedAt(userId);
    }

    /**
     * 실패한 동기화를 다시 시도한다 (스케줄러가 주기적으로 부른다).
     *
     * 팬아웃은 구글이 잠깐 흔들리거나 액세스 토큰 갱신이 실패해도 그대로 실패로 끝난다.
     * 그 상태로 두면 사용자 캘린더가 조용히 어긋난 채 남으므로 여기서 주워 담는다.
     *
     * 상한(maxRetry)을 넘긴 행은 다시 건드리지 않는다 — 권한 회수나 캘린더 삭제처럼
     * 재시도로 풀리지 않는 원인이면 무한히 두드려 봐야 할당량만 태운다.
     * 그런 행은 마이페이지 연동 상태의 실패 건수로 드러나고, 사용자가 재연동하면 정리된다.
     *
     * @return 이번 회차에 복구한 건수
     */
    public int retryFailed(int maxRetry, int limit) {
        List<UserCalendarEvent> targets = syncMapper.findRetryTargets(maxRetry, limit);
        if (targets.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (UserCalendarEvent mapping : targets) {
            GoogleCalendarLink link = calendarMapper.findByUserId(mapping.getUserId());
            if (link == null) {
                // 그사이 연동을 해제했다 — 더 시도할 이유가 없다
                syncMapper.markDeleted(mapping.getUserId(), mapping.getEventId());
                continue;
            }

            EventCalendarRow event = syncMapper.findEventForSync(mapping.getEventId());
            if (event == null) {
                // 일정이 삭제됐다(state != normal). 넣어둔 게 있으면 캘린더에서도 지운다
                removeFromCalendar(link, mapping);
                continue;
            }

            if (syncOne(link, event, mapping)) {
                recovered++;
            }
        }
        log.info("캘린더 동기화 재시도 - 대상 {}건, 복구 {}건", targets.size(), recovered);
        return recovered;
    }

    /**
     * 연동 해제 시 넣어둔 일정을 사용자 캘린더에서 지운다.
     *
     * 동기로 돈다 — 해제 응답에 "몇 건 지웠는지"를 실어 보내야 사용자가 결과를 확인할 수 있고,
     * 토큰을 폐기하기 전에 끝나야 하기 때문이다.
     *
     * @return 실제로 지운 건수
     */
    public int removeAllForUser(GoogleCalendarLink link) {
        List<UserCalendarEvent> mappings = syncMapper.findMappingsByUser(link.getUserId());
        if (mappings.isEmpty()) {
            return 0;
        }

        int removed = 0;
        String accessToken;
        try {
            accessToken = accessTokenOf(link);
        } catch (Exception e) {
            // 토큰이 이미 죽었으면 구글 쪽 일정은 손댈 수 없다. 로컬 매핑만 정리하고 넘어간다.
            log.warn("연동 해제 중 액세스 토큰 획득 실패 - userId={}, {}", link.getUserId(), e.getMessage());
            return 0;
        }

        for (UserCalendarEvent mapping : mappings) {
            if (mapping.getGoogleEventId() == null) {
                continue;
            }
            try {
                calendarClient.deleteEvent(accessToken, link.calendarIdOrDefault(), mapping.getGoogleEventId());
                syncMapper.markDeleted(link.getUserId(), mapping.getEventId());
                removed++;
            } catch (Exception e) {
                log.warn("연동 해제 중 일정 삭제 실패 - userId={}, eventId={}, {}",
                        link.getUserId(), mapping.getEventId(), e.getMessage());
            }
        }
        return removed;
    }

    // ─────────────────────── 내부 ───────────────────────

    /** 아직 반영되지 않은 일정만 넣는다 — 이미 들어간 것을 또 넣으면 사용자 캘린더에 중복으로 쌓인다. */
    private void syncIfAbsent(GoogleCalendarLink link, EventCalendarRow event) {
        UserCalendarEvent existing = syncMapper.findMapping(link.getUserId(), event.getEventId());
        if (existing != null && UserCalendarEvent.SYNCED.equals(existing.getSyncStatus())) {
            return;
        }
        syncOne(link, event, existing);
    }

    /**
     * 사용자 캘린더에 일정 하나를 반영한다 — 아직 없으면 넣고, 이미 있으면 고친다.
     * 추가·수정·재시도가 전부 이 경로를 지나므로 상태 기록이 한 곳에서만 일어난다.
     *
     * @param mapping 기존 매핑 (없으면 null)
     * @return 성공 여부
     */
    private boolean syncOne(GoogleCalendarLink link, EventCalendarRow event, UserCalendarEvent mapping) {
        Long userId = link.getUserId();
        Long eventId = event.getEventId();
        String googleEventId = mapping == null ? null : mapping.getGoogleEventId();

        if (googleEventId == null) {
            // 넣기 전에 흔적을 먼저 남긴다 — 호출 도중 죽어도 재시도 대상으로 잡히게
            UserCalendarEvent pending = new UserCalendarEvent();
            pending.setUserId(userId);
            pending.setEventId(eventId);
            pending.setSyncStatus(UserCalendarEvent.PENDING);
            pending.setRetryCount(mapping == null ? 0 : mapping.getRetryCount());
            syncMapper.upsertMapping(pending);
        }

        try {
            String accessToken = accessTokenOf(link);
            if (googleEventId == null) {
                googleEventId = calendarClient.insertEvent(accessToken, link.calendarIdOrDefault(), event);
            } else {
                calendarClient.updateEvent(accessToken, link.calendarIdOrDefault(), googleEventId, event);
            }
            syncMapper.markSynced(userId, eventId, googleEventId);
            return true;
        } catch (Exception e) {
            log.warn("캘린더 동기화 실패 - userId={}, eventId={}, {}", userId, eventId, e.getMessage());
            syncMapper.markFailed(userId, eventId, e.getMessage());
            return false;
        }
    }

    /** 사용자 캘린더에서 일정 하나를 지운다 (일정이 삭제됐거나 연동을 정리할 때). */
    private void removeFromCalendar(GoogleCalendarLink link, UserCalendarEvent mapping) {
        if (mapping.getGoogleEventId() == null) {
            // 애초에 들어간 적이 없다
            syncMapper.markDeleted(mapping.getUserId(), mapping.getEventId());
            return;
        }
        try {
            String accessToken = accessTokenOf(link);
            calendarClient.deleteEvent(accessToken, link.calendarIdOrDefault(), mapping.getGoogleEventId());
            syncMapper.markDeleted(mapping.getUserId(), mapping.getEventId());
        } catch (Exception e) {
            log.warn("캘린더 일정 삭제 실패 - userId={}, eventId={}, {}",
                    mapping.getUserId(), mapping.getEventId(), e.getMessage());
            syncMapper.markFailed(mapping.getUserId(), mapping.getEventId(), e.getMessage());
        }
    }

    /**
     * refresh token 으로 access token 을 새로 받는다.
     *
     * access token 을 캐시하지 않는 이유: 유효기간이 1시간인데 팬아웃은 드문드문 일어나고,
     * 캐시를 두면 만료 판정·동시성까지 따라붙는다. 갱신 호출 한 번이 더 싸다.
     */
    private String accessTokenOf(GoogleCalendarLink link) {
        String refreshToken = tokenCipher.decrypt(link.getRefreshToken());
        return oauthClient.refreshAccessToken(refreshToken).getAccessToken();
    }
}
