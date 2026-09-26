package com.back.admin.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** 앱 접속 점검 — 하루치 보고 (GET /api/admin/monitoring/app-launches) */
@Getter
@Setter
public class AppLaunchReportResponse {
    private LocalDate date;
    private LocalDate trackingSince;   // 앱 실행 기록이 시작된 날(app_launch_daily 첫 행). 없으면 null
    private int totalMembers;          // 점검 대상 회원 수 (탈퇴·영구차단 제외)
    private int launchedCount;         // 그날 앱을 연 회원 수
    private int notLaunchedCount;      // 안 연 회원 수
    private int webOnlyCount;          // 앱은 안 열고 웹으로만 들어온 회원 수
    private List<AppLaunchMemberResponse> members; // 안 연 사람(연속 미접속 긴 순) → 연 사람(이른 시각 순)
}
