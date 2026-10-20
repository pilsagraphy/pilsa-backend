package com.back.admin.monitoring.service;

import com.back.admin.monitoring.dto.BoardActivityResponse;
import com.back.admin.monitoring.dto.CalendarIssueResponse;
import com.back.admin.monitoring.dto.CohortRetentionRow;
import com.back.admin.monitoring.dto.ContentReactionResponse;
import com.back.admin.monitoring.dto.HealthSignalResponse;
import com.back.admin.monitoring.dto.DateCountRow;
import com.back.admin.monitoring.dto.MemberActivityRow;
import com.back.admin.monitoring.dto.NotificationReportResponse;
import com.back.admin.monitoring.dto.MonitoringSummaryResponse;
import com.back.admin.monitoring.dto.TrendingReportResponse;
import com.back.admin.monitoring.mapper.AdminInsightMapper;
import com.back.global.security.AuthUtils;
import com.back.mypage.calendar.service.GoogleCalendarSyncService;
import com.back.stats.policy.StatsPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
        // 간편로그인·캘린더 연동 현황 (PM 2026-10-13)
        r.setSocialLinkedMembers(mapper.countSocialLinkedMembers());
        r.setSocialByProvider(toCountMap(mapper.countSocialByProvider()));
        r.setSocialOnlyMembers(mapper.countSocialOnlyMembers());
        r.setCalendarLinkedMembers(mapper.countCalendarLinkedMembers());
        r.setCalendarByProvider(toCountMap(mapper.countCalendarByProvider()));
        return r;
    }

    private static java.util.Map<String, Integer> toCountMap(List<java.util.Map<String, Object>> rows) {
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
        for (java.util.Map<String, Object> row : rows) {
            Object cnt = row.get("cnt");
            m.put(String.valueOf(row.get("provider")), cnt == null ? 0 : ((Number) cnt).intValue());
        }
        return m;
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

    /** 가입 코호트 잔존 — 이번 주 포함 최근 weeks 주(월요일 기준) */
    public List<CohortRetentionRow> getCohortRetention(int weeks) {
        AuthUtils.requireAdmin();
        int n = Math.max(1, Math.min(weeks, 52));
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(n - 1L);
        return mapper.findCohortRetention(monday.atStartOfDay());
    }

    /** 최근 days 일 게시판별 반응 + 첫 댓글까지 걸린 시간 중앙값 */
    public ContentReactionResponse getContentReaction(int days) {
        AuthUtils.requireAdmin();
        int n = Math.max(1, Math.min(days, 365));
        LocalDateTime from = LocalDate.now().minusDays(n - 1L).atStartOfDay();
        ContentReactionResponse res = new ContentReactionResponse();
        res.setDays(n);
        res.setBoards(mapper.findContentReaction(from));
        Map<Long, List<Integer>> minutes = new HashMap<>();
        for (ContentReactionResponse.FirstCommentRow r : mapper.findFirstCommentMinutes(from)) {
            minutes.computeIfAbsent(r.getBoardId(), k -> new ArrayList<>()).add(r.getMinutes());
        }
        for (ContentReactionResponse.BoardRow b : res.getBoards()) {
            b.setMedianFirstCommentMinutes(median(minutes.get(b.getBoardId())));
        }
        return res;
    }

    /** 캘린더 연동 장애 — 실패 남은 회원(구글·카카오) · 토큰 갱신 실패(네이버) · staleDays 일 넘게 반영 없음 */
    public CalendarIssueResponse getCalendarIssues(int staleDays) {
        AuthUtils.requireAdmin();
        int days = Math.max(1, Math.min(staleDays, 365));
        CalendarIssueResponse res = new CalendarIssueResponse();
        res.setStaleDays(days);
        List<CalendarIssueResponse.Row> failed = mapper.findCalendarFailedMembers(GoogleCalendarSyncService.MAX_RETRY);
        List<CalendarIssueResponse.Row> naver = mapper.findNaverStuckMembers();
        List<CalendarIssueResponse.Row> stale = mapper.findStaleCalendarLinks(days);
        res.setGoogleFailedMembers((int) failed.stream().filter(r -> "google".equals(r.getProvider())).count());
        res.setKakaoFailedMembers((int) failed.stream().filter(r -> "kakao".equals(r.getProvider())).count());
        res.setNaverStuckMembers(naver.size());
        res.setStaleLinks(stale.size());
        res.getRows().addAll(failed);
        res.getRows().addAll(naver);
        res.getRows().addAll(stale);
        return res;
    }

    /** 기타 장애 신호 — DB 에 흔적이 남는 것만. 판정 기준: 접속 기록 3시간, 주간 가입 배치 2일 */
    public HealthSignalResponse getHealthSignals() {
        AuthUtils.requireAdmin();
        HealthSignalResponse r = mapper.findHealthSignals(GoogleCalendarSyncService.MAX_RETRY);
        LocalDateTime now = LocalDateTime.now();
        r.setAccessStale(r.getLastAccessHour() == null || r.getLastAccessHour().isBefore(now.minusHours(3)));
        r.setSignupBatchStale(r.getLastSignupCapturedAt() == null || r.getLastSignupCapturedAt().isBefore(now.minusDays(2)));
        return r;
    }

    /** 알림 발송·읽음 (PM 2026-10-20) — 기간(days) 안 종류별 합계와 날짜·종류별 묶음 */
    public NotificationReportResponse getNotificationReport(int days) {
        AuthUtils.requireAdmin();
        int d = Math.max(1, Math.min(days, 365));
        LocalDateTime from = LocalDate.now().minusDays(d - 1L).atStartOfDay();
        Map<String, List<Integer>> minutes = new HashMap<>();
        for (NotificationReportResponse.ReadMinutesRow row : mapper.findNotificationReadMinutes(from)) {
            if (row.getMinutes() != null) minutes.computeIfAbsent(row.getType(), k -> new ArrayList<>()).add(Math.max(0, row.getMinutes()));
        }
        NotificationReportResponse r = new NotificationReportResponse();
        r.setDays(d);
        List<NotificationReportResponse.TypeRow> types = mapper.findNotificationTypeStats(from);
        for (NotificationReportResponse.TypeRow t : types) {
            t.setReadRate(t.getSent() == 0 ? null : (double) t.getRead() / t.getSent());
            t.setMedianReadMinutes(median(minutes.get(t.getType())));
        }
        r.setTypes(types);
        r.setBatches(mapper.findNotificationBatches(from));
        return r;
    }

    /** 발송 묶음(그날·그 종류)의 수신자와 읽음 여부 */
    public List<NotificationReportResponse.RecipientRow> getNotificationRecipients(LocalDate date, String type) {
        AuthUtils.requireAdmin();
        return mapper.findNotificationRecipients(date, type);
    }

    private static Integer median(List<Integer> values) {
        if (values == null || values.isEmpty()) return null;
        List<Integer> v = new ArrayList<>(values);
        Collections.sort(v);
        int mid = v.size() / 2;
        return v.size() % 2 == 1 ? v.get(mid) : (v.get(mid - 1) + v.get(mid)) / 2;
    }

    private void fillDaysSince(MemberActivityRow m) {
        LocalDateTime base = m.getLastAccessAt() != null ? m.getLastAccessAt() : m.getJoinedAt();
        m.setDaysSince(base == null ? null : (int) ChronoUnit.DAYS.between(base.toLocalDate(), LocalDate.now()));
    }
}
