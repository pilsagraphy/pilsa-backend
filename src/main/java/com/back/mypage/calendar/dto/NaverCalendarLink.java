package com.back.mypage.calendar.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** user_naver_calendar 행 — 네이버 캘린더에 '새 일정 넣기' 용 토큰 (추가만 된다, 수정·삭제 API 없음). 토큰은 AES-GCM 암호화 저장 */
@Getter
@Setter
public class NaverCalendarLink {
    private Long userId;
    private String naverEmail;
    private byte[] accessToken;
    private LocalDateTime accessExpiresAt;
    private byte[] refreshToken;
    private LocalDateTime linkedAt;
    private LocalDateTime lastSentAt;
    private int sentCount;
}
