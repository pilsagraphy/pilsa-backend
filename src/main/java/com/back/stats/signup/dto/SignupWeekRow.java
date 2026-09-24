package com.back.stats.signup.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

// 주간 신규가입 집계 1행 (stats_signup_weekly)
@Getter
@Setter
public class SignupWeekRow {
    private LocalDate statWeek;    // 주 시작일(월요일)
    private int signupCount;       // 그 주 가입 수 (탈퇴자 포함 — 가입 사실은 변하지 않는다)
    private int studentCount;      // 그중 재학생 (집계 시점 member_type 스냅샷)
    private int alumniCount;       // 그중 졸업생
}
