package com.back.admin.monitoring.service;

import com.back.admin.monitoring.dto.BoardActivityResponse;
import com.back.admin.monitoring.dto.DateCountRow;
import com.back.admin.monitoring.dto.MemberActivityRow;
import com.back.admin.monitoring.dto.MonitoringSummaryResponse;
import com.back.admin.monitoring.dto.TrendingReportResponse;
import com.back.admin.monitoring.mapper.AdminInsightMapper;
import com.back.global.security.AuthUtils;
import com.back.stats.policy.StatsPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 모니터링 고도화 (PM 2026-10-10 밤 "보통의 커뮤니티가 필요한 내용") — 요약 카드(DAU/WAU/MAU·글·댓글·신고·푸시),
 * N일 이상 미접속 회원, 게시판별 활동과 날짜별 글·댓글, 푸시 미등록 회원, 활동 상위 회원, 급상승 판정 기준값.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInsightService {

    private final AdminInsightMapper mapper;
    private final StatsPolicy statsPolicy;

    public MonitoringSummaryResponse getSummary() {
        AuthUtils.requireAdmin();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        MonitoringSummaryResponse r = new MonitoringSummaryResponse();
        r.setDau(mapper.countActiveUsersSince(todayStart));
        r.setWau(mapper.countActiveUsersSince(today.minusDays(6).atStartOfDay()));
        r.setMau(mapper.countActiveUsersSince(today.minusDays(29).atStartOfDay()));
        r.setMembersTotal(mapper.countMembers());
        r.setSignupsThisMonth(mapper.countSignupsSince(today.withDayOfMonth(1).atStartOfDay()));
        LocalDateTime d7 = today.minusDays(6).atStartOfDay();
        LocalDateTime d30 = today.minusDays(29).atStartOfDay();
        r.setPostsLast7(mapper.countPostsSince(d7));
        r.setCommentsLast7(mapper.countCommentsSince(d7));
        r.setPostsLast30(mapper.countPostsSince(d30));
        r.setCommentsLast30(mapper.countCommentsSince(d30));
        r.setPendingReports(mapper.countPendingReportTargets());
        r.setPushRegisteredMembers(mapper.countPushRegisteredMembers());
        return r;
    }

    public List<MemberActivityRow> getInactiveMembers(int days) {
        AuthUtils.requireAdmin();
        List<MemberActivityRow> rows = mapper.findInactiveMembers(Math.max(1, Math.min(days, 365)));
        rows.forEach(this::fillDaysSince);
        return rows;
    }

    public List<MemberActivityRow> getPushUnregisteredMembers() {
        AuthUtils.requireAdmin();
        List<MemberActivityRow> rows = mapper.findPushUnregisteredMembers();
        rows.forEach(this::fillDaysSince);
        return rows;
    }

    public List<MemberActivityRow> getTopMembers(int days, int limit) {
        AuthUtils.requireAdmin();
        LocalDateTime from = LocalDate.now().minusDays(Math.max(1, Math.min(days, 365)) - 1L).atStartOfDay();
        List<MemberActivityRow> rows = mapper.findTopMembers(from, Math.max(1, Math.min(limit, 50)));
        rows.forEach(this::fillDaysSince);
        return rows;
    }

    public BoardActivityResponse getBoardActivity(int days) {
        AuthUtils.requireAdmin();
        int n = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now();
        LocalDate fromDate = today.minusDays(n - 1L);
        BoardActivityResponse res = new BoardActivityResponse();
        res.setDays(n);
        res.setBoards(mapper.findBoardActivity(fromDate.atStartOfDay()));
        Map<LocalDate, Integer> posts = new HashMap<>();
        for (DateCountRow r : mapper.countDailyPosts(fromDate)) posts.put(r.getStatDate(), r.getCount());
        Map<LocalDate, Integer> comments = new HashMap<>();
        for (DateCountRow r : mapper.countDailyComments(fromDate)) comments.put(r.getStatDate(), r.getCount());
        // 빈 날도 0 으로 채워 그래프가 끊기지 않게
        for (LocalDate d = fromDate; !d.isAfter(today); d = d.plusDays(1)) {
            BoardActivityResponse.DayRow row = new BoardActivityResponse.DayRow();
            row.setDate(d);
            row.setPosts(posts.getOrDefault(d, 0));
            row.setComments(comments.getOrDefault(d, 0));
            res.getDaily().add(row);
        }
        return res;
    }

    /** 급상승 3관문의 지금 기준값 — 화면이 미선정 행에 걸린 관문을 적는 데 쓴다 */
    public TrendingReportResponse getTrendingPolicy() {
        AuthUtils.requireAdmin();
        return new TrendingReportResponse(statsPolicy.trendingMinScore(), statsPolicy.trendingSpikeRatio(), statsPolicy.trendingTopN(), List.of());
    }

    private void fillDaysSince(MemberActivityRow m) {
        LocalDateTime base = m.getLastAccessAt() != null ? m.getLastAccessAt() : m.getJoinedAt();
        m.setDaysSince(base == null ? null : (int) ChronoUnit.DAYS.between(base.toLocalDate(), LocalDate.now()));
    }
}
