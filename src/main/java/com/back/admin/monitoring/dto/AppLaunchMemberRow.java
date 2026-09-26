package com.back.admin.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 회원 1명 + 그날의 앱 실행 · 웹 접속 (매퍼 행) */
@Getter
@Setter
public class AppLaunchMemberRow {
    private Long userId;
    private String name;
    private String memberType;
    private Integer adminLevel;
    private LocalDate joinedDate;        // users.created_at 의 날짜 — 가입 전 날은 미접속으로 세지 않는다
    private LocalDateTime launchedAt;    // 그날 처음 앱을 연 시각 (없으면 null)
    private Integer launchCount;         // 그날 앱을 연 횟수 (없으면 null)
    private LocalDateTime webAccessedAt; // 그날 첫 인증 요청 시각(stats_access_hourly, 브라우저·앱 구분 없음)
}
