package com.back.admin.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 일별 접속 추이 한 점 — 활성 회원 수(모든 채널)와 앱 실행 회원 수 */
@Getter
@Setter
@AllArgsConstructor
public class DailyAccessResponse {
    private LocalDate date;
    private int activeUsers;  // stats_access_hourly 그날 DISTINCT user_id (브라우저 + 앱)
    private int appLaunches;  // app_launch_daily 그날 행 수 (앱으로 연 회원 수)
}
