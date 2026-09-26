package com.back.admin.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** app_launch_daily 의 (회원, 날짜) 한 쌍 — 연속 미접속 일수 계산용 */
@Getter
@Setter
public class AppLaunchDateRow {
    private Long userId;
    private LocalDate launchDate;
}
