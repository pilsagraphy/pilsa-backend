package com.back.mypage.calendar.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 구글 캘린더 연동 상태 (마이페이지 토글용).
 *
 * failedCount 를 내려주는 이유: 토큰이 죽었거나 사용자가 구글에서 권한을 회수하면
 * 연동은 "켜짐"인데 일정이 안 들어가는 상태가 된다. 프론트가 이 값으로 재연동을 안내한다.
 */
@Getter
@AllArgsConstructor
public class CalendarLinkStatusResponse {
    private boolean linked;
    private String googleEmail;
    private String lastSyncedAt;
    private int syncedCount;
    private int failedCount;

    public static CalendarLinkStatusResponse notLinked() {
        return new CalendarLinkStatusResponse(false, null, null, 0, 0);
    }
}
