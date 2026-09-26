package com.back.admin.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 주간 신규 가입 (stats_signup_weekly 스냅샷) */
@Getter
@Setter
public class SignupWeekResponse {
    private LocalDate statWeek;   // 주 시작일(월요일)
    private int signupCount;
    private int studentCount;
    private int alumniCount;
    private LocalDateTime capturedAt;
}
