package com.back.mypage.calendar.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * user_google_calendar 행 — 일정을 넣어 줄 구글 캘린더와 그 권한.
 *
 * 소셜 로그인 연결(user_social_accounts)과 별개다.
 * 캘린더만 해제해도 로그인 연결은 남고, 로그인만 연결하고 캘린더는 안 쓸 수도 있다.
 */
@Getter
@Setter
public class GoogleCalendarLink {
    private Long userId;
    private String googleEmail;

    /** 암호화된 상태 그대로 담긴다. 복호화는 TokenCipher 를 거친다. */
    private byte[] refreshToken;
    private String scopes;
    private String calendarId;
    private LocalDateTime linkedAt;
    private LocalDateTime lastSyncedAt;

    public String calendarIdOrDefault() {
        return calendarId == null ? "primary" : calendarId;
    }
}
