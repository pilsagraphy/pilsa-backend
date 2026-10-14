package com.back.admin.monitoring.controller;

import com.back.admin.monitoring.dto.BoardActivityResponse;
import com.back.admin.monitoring.dto.CalendarIssueResponse;
import com.back.admin.monitoring.dto.CohortRetentionRow;
import com.back.admin.monitoring.dto.ContentReactionResponse;
import com.back.admin.monitoring.dto.HealthSignalResponse;
import com.back.admin.monitoring.dto.MemberActivityRow;
import com.back.admin.monitoring.dto.MonitoringSummaryResponse;
import com.back.admin.monitoring.dto.TrendingReportResponse;
import com.back.admin.monitoring.service.AdminInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 운영 관리 > 모니터링 고도화 (PM 2026-10-10 밤). 전부 관리자(Lv1+) 조회 전용 */
@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-운영 관리-모니터링(고도화)", description = "요약 카드 · 미접속 회원 · 게시판 활동 · 푸시 미등록 · 활동 상위 · 급상승 기준값")
public class AdminInsightController {

    private final AdminInsightService service;

    @Operation(summary = "요약 카드", description = """
            `{ dau, wau, mau, membersTotal, signupsThisMonth, postsLast7, commentsLast7, postsLast30, commentsLast30, pendingReports, pushRegisteredMembers,
               socialLinkedMembers, socialByProvider:{GOOGLE,NAVER,KAKAO}, socialOnlyMembers, calendarLinkedMembers, calendarByProvider:{google,naver,kakao} }`
            접속은 stats_access_hourly(로그인 요청이 있던 시간대) 기준. pendingReports 는 대상(글·댓글) 단위""")
    @GetMapping("/api/admin/monitoring/summary")
    public ResponseEntity<MonitoringSummaryResponse> summary() {
        return ResponseEntity.ok(service.getSummary());
    }

    @Operation(summary = "N일 이상 미접속 회원", description = "마지막 접속(접속 기록, 없으면 last_login_at)이 days 일보다 오래된 회원. 오래된 순. `[{ userId, name, loginId, memberType, adminLevel, joinedAt, lastAccessAt, daysSince, deviceCount }]`")
    @GetMapping("/api/admin/monitoring/inactive")
    public ResponseEntity<List<MemberActivityRow>> inactive(@Parameter(description = "며칠 이상 (기본 14, 최대 365)") @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(service.getInactiveMembers(days));
    }

    @Operation(summary = "게시판 활동", description = "기간 안 게시판별 글·댓글·좋아요 수 + 날짜별 전체 글·댓글 수. `{ days, boards: [{ boardId, boardName, postCount, commentCount, likeCount }], daily: [{ date, posts, comments }] }`")
    @GetMapping("/api/admin/monitoring/boards/activity")
    public ResponseEntity<BoardActivityResponse> boardActivity(@Parameter(description = "최근 며칠 (기본 30, 최대 365)") @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.getBoardActivity(days));
    }

    @Operation(summary = "푸시 미등록 회원", description = "알림 수신 기기를 하나도 등록하지 않은 회원, 최근 접속 순 (미접속 목록과 같은 행 모양)")
    @GetMapping("/api/admin/monitoring/push/unregistered")
    public ResponseEntity<List<MemberActivityRow>> pushUnregistered() {
        return ResponseEntity.ok(service.getPushUnregisteredMembers());
    }

    @Operation(summary = "활동 상위 회원", description = "기간 안 글+댓글이 많은 순. `postCount`·`commentCount` 는 기간 안 수")
    @GetMapping("/api/admin/monitoring/top-members")
    public ResponseEntity<List<MemberActivityRow>> topMembers(
            @Parameter(description = "최근 며칠 (기본 30)") @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "몇 명 (기본 10, 최대 50)") @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.getTopMembers(days, limit));
    }

    @Operation(summary = "급상승 판정 기준값", description = "`{ minScore, spikeRatio, topN, rows: [] }` — 화면이 미선정 행에 걸린 관문(점수 미달·배수 미달·순위 밖)을 적는 데 쓴다. policy_settings 의 지금 값")
    @GetMapping("/api/admin/monitoring/trending/policy")
    public ResponseEntity<TrendingReportResponse> trendingPolicy() {
        return ResponseEntity.ok(service.getTrendingPolicy());
    }

    @Operation(summary = "가입 코호트 잔존", description = """
            가입 주(월요일 기준)별로 가입일+7N ~ +7N+7 일 창에 한 번이라도 접속(stats_access_hourly)한 회원 수. N=1·2·4.
            `[{ weekStart, signups, w1Eligible, w1Retained, w2Eligible, w2Retained, w4Eligible, w4Retained }]` — eligible 은 창이 다 지난 회원(분모), 최근 주부터""")
    @GetMapping("/api/admin/monitoring/cohort-retention")
    public ResponseEntity<List<CohortRetentionRow>> cohortRetention(@Parameter(description = "최근 몇 주 (기본 12, 최대 52)") @RequestParam(defaultValue = "12") int weeks) {
        return ResponseEntity.ok(service.getCohortRetention(weeks));
    }

    @Operation(summary = "콘텐츠 반응", description = """
            기간 안 정상 글의 게시판별 반응. 댓글은 state='normal' 이고 비밀 댓글이 아닌 것만.
            `{ days, boards: [{ boardId, boardName, postCount, commentCount, likeCount, zeroCommentPosts, medianFirstCommentMinutes }] }` — 첫 댓글은 글쓴이 본인 댓글 제외, 댓글 달린 글이 없으면 null""")
    @GetMapping("/api/admin/monitoring/content-reaction")
    public ResponseEntity<ContentReactionResponse> contentReaction(@Parameter(description = "최근 며칠 (기본 30, 최대 365)") @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.getContentReaction(days));
    }

    @Operation(summary = "캘린더 연동 장애", description = """
            `{ staleDays, googleFailedMembers, kakaoFailedMembers, naverStuckMembers, staleLinks,
               rows: [{ provider: google|kakao|naver, issue: FAILED|TOKEN|STALE, userId, name, loginId, memberType, failedCount, exhaustedCount, lastError, lastReflectedAt }] }`
            FAILED = 구글·카카오 동기화 실패 매핑이 남은 회원, TOKEN = 네이버 토큰 갱신 실패로 멈춘 회원(만료 뒤 등록된 일정이 있는데 토큰이 그대로),
            STALE = 마지막 반영(last_synced_at / last_sent_at, 없으면 연동 시각)이 staleDays 일 넘게 없는 연동""")
    @GetMapping("/api/admin/monitoring/calendar/issues")
    public ResponseEntity<CalendarIssueResponse> calendarIssues(@Parameter(description = "며칠 넘게 반영 없으면 (기본 7)") @RequestParam(defaultValue = "7") int staleDays) {
        return ResponseEntity.ok(service.getCalendarIssues(staleDays));
    }

    @Operation(summary = "기타 장애 신호", description = """
            `{ lastAccessHour, lastTrendingHour, lastSignupCapturedAt, calendarExhaustedMembers, accessStale, signupBatchStale }`
            DB 에 흔적이 남는 것만 — accessStale: 접속 기록 3시간 넘게 없음, signupBatchStale: 주간 가입 배치(04:50) 2일 넘게 없음.
            lastTrendingHour 는 적재 컷 미달 구간엔 행이 없어 판정하지 않는다. 푸시·메일 실패, 5xx, 정리 배치 이력은 DB 에 없다(서버 로그)""")
    @GetMapping("/api/admin/monitoring/health-signals")
    public ResponseEntity<HealthSignalResponse> healthSignals() {
        return ResponseEntity.ok(service.getHealthSignals());
    }
}
