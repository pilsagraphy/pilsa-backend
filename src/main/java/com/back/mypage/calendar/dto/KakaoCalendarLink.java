package com.back.mypage.calendar.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** user_kakao_calendar 행 — 톡캘린더 자동 등록용 토큰 (AES-GCM 암호화). 구글과 같이 등록·수정·삭제를 다 반영한다 */
@Getter
@Setter
public class KakaoCalendarLink {
    private Long userId;
    private String kakaoName; // 닉네임 또는 이메일 — 표시용
    private byte[] accessToken;
    private LocalDateTime accessExpiresAt;
    private byte[] refreshToken;
    private LocalDateTime linkedAt;
    private LocalDateTime lastSyncedAt;
}
