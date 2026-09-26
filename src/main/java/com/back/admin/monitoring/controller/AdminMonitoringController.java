package com.back.admin.monitoring.controller;

import com.back.admin.monitoring.dto.AppLaunchReportResponse;
import com.back.admin.monitoring.dto.DailyAccessResponse;
import com.back.admin.monitoring.dto.HourCountRow;
import com.back.admin.monitoring.dto.SignupWeekResponse;
import com.back.admin.monitoring.dto.TrendingPostResponse;
import com.back.admin.monitoring.service.AdminMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-운영 관리-모니터링",
        description = "앱 접속 점검(회원별) + 통계 그래프(일별 접속 · 시간대별 접속 · 주간 가입 · 급상승). 관리자(admin_level ≥ 1) 전용.")
public class AdminMonitoringController {

    private final AdminMonitoringService service;

    @Operation(summary = "앱 접속 점검 (회원별, 하루)", description = """
            그날 설치형 앱(시작 URL /?launch=app)으로 들어온 회원과 안 들어온 회원 명단. Play 테스트 기간의 "매일 앱 접속" 조건 점검용.
            ### 응답 예시
            ```json
            { "date": "2026-09-26", "trackingSince": "2026-09-26", "totalMembers": 74,
              "launchedCount": 12, "notLaunchedCount": 62, "webOnlyCount": 5,
              "members": [ { "userId": 8, "name": "박수민", "memberType": "STUDENT", "adminLevel": 2, "joinedDate": "2026-03-18",
                             "launched": false, "launchedAt": null, "launchCount": null, "webAccessedAt": "2026-09-26T09:10:00",
                             "lastLaunchDate": "2026-09-24", "missStreak": 2 } ] }
            ```
            - 대상: 탈퇴·영구차단 제외 회원 전원 (관리자 홈 '전체 회원 수'와 같은 기준). 아이폰 회원은 앱이 없어 원래 대상이 아니다.
            - launched: 그날 앱을 열었는가. webAccessedAt: 그날 첫 인증 요청(브라우저·앱 구분 없음) — 앱은 안 열고 웹으로만 들어온 사람 구분용.
            - missStreak: 그날까지 연속 미접속 일수(그날 열었으면 0). 가입일·기록 시작일 이전은 세지 않고 최대 60.
            - 정렬: 안 연 사람(연속 미접속 긴 순) → 연 사람(이른 시각 순).
            - date 생략 시 오늘(서버 날짜).""")
    @GetMapping("/api/admin/monitoring/app-launches")
    public ResponseEntity<AppLaunchReportResponse> appLaunches(
            @Parameter(description = "점검할 날짜 YYYY-MM-DD (기본 오늘)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getAppLaunchReport(date != null ? date : LocalDate.now()));
    }

    @Operation(summary = "일별 접속 추이", description = """
            최근 N일의 하루 활성 회원 수(stats_access_hourly DISTINCT user_id, 브라우저+앱)와 앱 실행 회원 수(app_launch_daily).
            빈 날은 0 으로 채워 오래된 날부터 준다. `[{ "date": "2026-09-25", "activeUsers": 31, "appLaunches": 12 }]`""")
    @GetMapping("/api/admin/monitoring/access/daily")
    public ResponseEntity<List<DailyAccessResponse>> dailyAccess(
            @Parameter(description = "오늘 포함 일수 (기본 30, 1~366)") @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.getDailyAccess(days));
    }

    @Operation(summary = "시간대별 접속 (하루)", description = """
            그날 0~23시 각 시간 버킷의 활성 회원 수. 빈 시간은 0. `[{ "hour": 9, "count": 4 }]`""")
    @GetMapping("/api/admin/monitoring/access/hourly")
    public ResponseEntity<List<HourCountRow>> hourlyAccess(
            @Parameter(description = "날짜 YYYY-MM-DD (기본 오늘)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getHourlyAccess(date != null ? date : LocalDate.now()));
    }

    @Operation(summary = "주간 신규 가입", description = """
            stats_signup_weekly 스냅샷 최근 N주 (오래된 주부터). 탈퇴자 포함 — 가입 사실은 변하지 않는다.
            `[{ "statWeek": "2026-09-14", "signupCount": 3, "studentCount": 3, "alumniCount": 0, "capturedAt": "..." }]`""")
    @GetMapping("/api/admin/monitoring/signups/weekly")
    public ResponseEntity<List<SignupWeekResponse>> weeklySignups(
            @Parameter(description = "주 수 (기본 12, 1~104)") @RequestParam(defaultValue = "12") int weeks) {
        return ResponseEntity.ok(service.getWeeklySignups(weeks));
    }

    @Operation(summary = "급상승 집계", description = """
            stats_post_hourly 최근 N시간 행(최신 구간 · 순위순). onlyTrending=true 면 3관문을 통과해 선정된 행만.
            관리자 화면이라 read_scope 필터 없이 전부 본다.""")
    @GetMapping("/api/admin/monitoring/trending")
    public ResponseEntity<List<TrendingPostResponse>> trending(
            @Parameter(description = "최근 몇 시간 (기본 48, 최대 720)") @RequestParam(defaultValue = "48") int hours,
            @Parameter(description = "선정된 행만 (기본 false)") @RequestParam(defaultValue = "false") boolean onlyTrending,
            @Parameter(description = "최대 행 수 (기본 50, 최대 200)") @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(service.getTrending(hours, onlyTrending, limit));
    }
}
