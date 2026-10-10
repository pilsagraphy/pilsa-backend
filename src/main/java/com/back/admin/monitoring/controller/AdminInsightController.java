package com.back.admin.monitoring.controller;

import com.back.admin.monitoring.dto.BoardActivityResponse;
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
            `{ dau, wau, mau, membersTotal, signupsThisMonth, postsLast7, commentsLast7, postsLast30, commentsLast30, pendingReports, pushRegisteredMembers }`
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
}
