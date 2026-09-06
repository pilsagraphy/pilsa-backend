package com.back.mypage.calendar.service;

import com.back.global.oauth.GoogleIntegrationException;
import com.back.global.oauth.GoogleOAuthClient;
import com.back.global.oauth.GoogleProperties;
import com.back.global.oauth.OAuthStateService;
import com.back.global.oauth.TokenCipher;
import com.back.global.oauth.dto.GoogleTokenResponse;
import com.back.global.oauth.dto.GoogleUserInfo;
import com.back.mypage.calendar.dto.CalendarLinkStatusResponse;
import com.back.mypage.calendar.dto.GoogleCalendarLink;
import com.back.mypage.calendar.dto.UserCalendarEvent;
import com.back.mypage.calendar.mapper.CalendarSyncMapper;
import com.back.mypage.calendar.mapper.GoogleCalendarMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * 구글 캘린더 연동 관리 (연동 시작 / 상태 / 해제).
 * 실제 일정 반영은 {@link GoogleCalendarSyncService} 가 담당한다.
 *
 * 소셜 로그인(auth.social)과 독립이다 — 테이블도 따로고, 구글로 로그인하지 않는 회원도
 * 캘린더만 연동할 수 있다. 그래서 이 패키지는 auth.social 을 참조하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GoogleProperties properties;
    private final GoogleOAuthClient oauthClient;
    private final OAuthStateService stateService;
    private final GoogleCalendarMapper calendarMapper;
    private final CalendarSyncMapper syncMapper;
    private final GoogleCalendarSyncService syncService;
    private final TokenCipher tokenCipher;

    /**
     * 캘린더 연동 동의 화면 URL.
     * offline=true 로 refresh token 을 확보한다 — 관리자가 일정을 바꿀 때
     * 사용자가 접속해 있지 않아도 서버가 그 사람 캘린더를 고칠 수 있어야 한다.
     */
    public String buildAuthorizeUrl(Long userId) {
        String state = stateService.issue(OAuthStateService.PURPOSE_CALENDAR, userId);
        return oauthClient.buildAuthorizeUrl(
                GoogleProperties.CALENDAR_SCOPE, properties.getCalendarRedirectUri(), state, true);
    }

    /**
     * 연동 콜백. 구글이 브라우저 리다이렉트로 부르므로 인증 헤더가 없다 —
     * 사용자는 state 로 식별한다.
     *
     * @return 초기 동기화를 시작할 사용자 id
     */
    @Transactional
    public Long completeLink(String code, String state) {
        Long userId = stateService.consume(state, OAuthStateService.PURPOSE_CALENDAR);
        if (userId == null) {
            throw new GoogleIntegrationException("잘못된 인증 요청입니다.", HttpStatus.BAD_REQUEST);
        }

        GoogleTokenResponse token = oauthClient.exchangeCode(code, properties.getCalendarRedirectUri());

        // 사용자가 동의 화면에서 캘린더 체크를 해제할 수 있다. 그러면 토큰은 오지만 권한이 없다.
        if (token.getScope() == null || !token.getScope().contains("calendar.events")) {
            throw new GoogleIntegrationException("캘린더 권한에 동의해야 연동할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        if (token.getRefreshToken() == null) {
            // prompt=consent 를 붙였으므로 정상 흐름에서는 오지 않는다.
            // 그래도 없으면 이후 갱신이 불가능하므로 연동을 완료로 처리하면 안 된다.
            throw new GoogleIntegrationException(
                    "구글에서 갱신 토큰을 받지 못했습니다. 구글 계정 설정에서 기존 권한을 삭제한 뒤 다시 시도해주세요.",
                    HttpStatus.BAD_GATEWAY);
        }

        // 어느 구글 계정 캘린더에 넣는지 화면에 보여줘야 하므로 이메일을 같이 저장한다.
        // (소셜 로그인 연결과 다른 계정일 수도 있다 — 로그인은 A 계정, 캘린더는 B 계정)
        GoogleUserInfo info = oauthClient.parseIdToken(token.getIdToken());

        GoogleCalendarLink link = new GoogleCalendarLink();
        link.setUserId(userId);
        link.setGoogleEmail(info.getEmail());
        link.setRefreshToken(tokenCipher.encrypt(token.getRefreshToken()));
        link.setScopes(token.getScope());
        link.setCalendarId("primary");
        calendarMapper.upsertLink(link);

        log.info("구글 캘린더 연동 완료 - userId={}", userId);
        return userId;
    }

    /** 연동 완료 후 초기 동기화 시작 (비동기). 트랜잭션이 커밋된 뒤 호출해야 한다. */
    public void startInitialSync(Long userId) {
        syncService.syncAllForUser(userId);
    }

    public CalendarLinkStatusResponse getStatus(Long userId) {
        GoogleCalendarLink link = calendarMapper.findByUserId(userId);
        if (link == null) {
            return CalendarLinkStatusResponse.notLinked();
        }

        // 실패 건수는 "재시도 배치가 손을 뗀 것"만 센다. 아직 재시도 대기 중인 건까지 세면
        // 곧 저절로 복구될 일에 대고 사용자에게 재연동을 안내하게 된다.
        return new CalendarLinkStatusResponse(
                true,
                link.getGoogleEmail(),
                link.getLastSyncedAt() == null ? null : link.getLastSyncedAt().format(FORMATTER),
                syncMapper.countByUserAndStatus(userId, UserCalendarEvent.SYNCED),
                syncMapper.countExhausted(userId, GoogleCalendarSyncService.MAX_RETRY)
        );
    }

    /**
     * 연동 해제.
     *
     * @param removeEvents 넣어둔 일정까지 사용자 캘린더에서 지울지
     * @return 지운 일정 건수
     */
    @Transactional
    public int unlink(Long userId, boolean removeEvents) {
        GoogleCalendarLink link = calendarMapper.findByUserId(userId);
        if (link == null) {
            throw new GoogleIntegrationException("연동된 구글 캘린더가 없습니다.", HttpStatus.NOT_FOUND);
        }

        int removed = 0;
        if (removeEvents) {
            removed = syncService.removeAllForUser(link);
        }

        // 토큰 폐기는 실패해도 무시된다(GoogleOAuthClient.revoke). 우리 쪽 연동은 반드시 끊는다.
        try {
            oauthClient.revoke(tokenCipher.decrypt(link.getRefreshToken()));
        } catch (Exception e) {
            log.warn("구글 토큰 폐기 생략 - userId={}, {}", userId, e.getMessage());
        }

        calendarMapper.deleteByUserId(userId);
        syncMapper.deleteByUserId(userId);

        log.info("구글 캘린더 연동 해제 - userId={}, 삭제한 일정={}건", userId, removed);
        return removed;
    }
}
