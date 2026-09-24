package com.back.mypage.calendar.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** user_calendar_events 행 (사용자별 구글 이벤트 매핑). */
@Getter
@Setter
public class UserCalendarEvent {
    private Long id;
    private Long userId;
    private Long eventId;
    private String googleEventId;
    private String syncStatus;
    private int retryCount;
    private String lastError;
    private LocalDateTime lastSyncedAt;

    public static final String PENDING = "PENDING";
    public static final String SYNCED  = "SYNCED";
    public static final String FAILED  = "FAILED";
    public static final String DELETED = "DELETED";
}
