package com.back.admin.monitoring.service;

import com.back.admin.monitoring.dto.AppLaunchDateRow;
import com.back.admin.monitoring.dto.AppLaunchMemberResponse;
import com.back.admin.monitoring.dto.AppLaunchMemberRow;
import com.back.admin.monitoring.dto.AppLaunchReportResponse;
import com.back.admin.monitoring.dto.DailyAccessResponse;
import com.back.admin.monitoring.dto.DateCountRow;
import com.back.admin.monitoring.dto.HourCountRow;
import com.back.admin.monitoring.dto.SignupWeekResponse;
import com.back.admin.monitoring.dto.TrendingPostResponse;
import com.back.admin.monitoring.mapper.AdminMonitoringMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 운영 관리 > 모니터링.
 *
 * 앱 접속 점검은 Play 비공개 테스트 조건("매일 앱 아이콘으로 접속")을 회원별로 확인하려는 것이다.
 * 연속 미접속 일수는 가입일과 기록 시작일 이전을 세지 않는다 — 기록이 없던 날을 "안 열었다"로 몰면 억울하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMonitoringService {

    /** 연속 미접속 계산 상한(일). 그 이상은 "N+" 로 보는 게 맞고, 조회 범위도 이만큼만 본다 */
    static final int MAX_STREAK_DAYS = 60;

    private final AdminMonitoringMapper mapper;

    public AppLaunchReportResponse getAppLaunchReport(LocalDate date) {
        AuthUtils.requireAdmin();

        LocalDate trackingSince = mapper.findTrackingSince();
        List<AppLaunchMemberRow> rows = mapper.findMembersWithLaunch(date);

        // 기준일부터 거슬러 MAX_STREAK_DAYS 안의 실행일 집합 (회원별)
        LocalDate windowStart = date.minusDays(MAX_STREAK_DAYS);
        Map<Long, Set<LocalDate>> launchDays = new HashMap<>();
        for (AppLaunchDateRow r : mapper.findLaunchDatesBetween(windowStart, date)) {
            launchDays.computeIfAbsent(r.getUserId(), k -> new HashSet<>()).add(r.getLaunchDate());
        }
        Map<Long, LocalDate> lastLaunch = new HashMap<>();
        for (AppLaunchDateRow r : mapper.findLastLaunchDates(date)) {
            lastLaunch.put(r.getUserId(), r.getLaunchDate());
        }

        List<AppLaunchMemberResponse> members = new ArrayList<>(rows.size());
        int launched = 0;
        int webOnly = 0;
        for (AppLaunchMemberRow row : rows) {
            AppLaunchMemberResponse m = new AppLaunchMemberResponse();
            m.setUserId(row.getUserId());
            m.setName(row.getName());
            m.setMemberType(row.getMemberType());
            m.setAdminLevel(row.getAdminLevel());
            m.setJoinedDate(row.getJoinedDate());
            m.setLaunched(row.getLaunchedAt() != null);
            m.setLaunchedAt(row.getLaunchedAt());
            m.setLaunchCount(row.getLaunchCount());
            m.setWebAccessedAt(row.getWebAccessedAt());
            m.setLastLaunchDate(lastLaunch.get(row.getUserId()));
            m.setMissStreak(missStreak(date, trackingSince, row.getJoinedDate(),
                    launchDays.getOrDefault(row.getUserId(), Set.of())));
            if (m.getLaunched()) {
                launched++;
            } else if (row.getWebAccessedAt() != null) {
                webOnly++;
            }
            members.add(m);
        }

        // 안 연 사람을 위로 — 연속 미접속이 긴 순, 그다음 이름. 연 사람은 이른 시각 순
        members.sort(Comparator
                .comparing((AppLaunchMemberResponse m) -> m.getLaunched())
                .thenComparing((AppLaunchMemberResponse m) -> m.getLaunched() ? 0 : -m.getMissStreak())
                .thenComparing(m -> m.getLaunched() ? m.getLaunchedAt().toString() : m.getName()));

        AppLaunchReportResponse res = new AppLaunchReportResponse();
        res.setDate(date);
        res.setTrackingSince(trackingSince);
        res.setTotalMembers(rows.size());
        res.setLaunchedCount(launched);
        res.setNotLaunchedCount(rows.size() - launched);
        res.setWebOnlyCount(webOnly);
        res.setMembers(members);
        return res;
    }

    /**
     * 기준일까지 연속으로 앱을 안 연 일수. 기준일에 열었으면 0.
     * 세는 범위의 시작 = max(가입일, 기록 시작일). 기록 시작 전 · 가입 전 날짜는 세지 않는다.
     */
    static int missStreak(LocalDate date, LocalDate trackingSince, LocalDate joinedDate, Set<LocalDate> launchDays) {
        LocalDate floor = joinedDate;
        if (trackingSince != null && (floor == null || trackingSince.isAfter(floor))) {
            floor = trackingSince;
        }
        if (floor == null) {
            // 기록이 하나도 없으면 아직 셀 근거가 없다
            return 0;
        }
        int streak = 0;
        LocalDate cursor = date;
        while (!cursor.isBefore(floor) && streak < MAX_STREAK_DAYS) {
            if (launchDays.contains(cursor)) {
                break;
            }
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public List<DailyAccessResponse> getDailyAccess(int days) {
        AuthUtils.requireAdmin();
        int n = Math.max(1, Math.min(days, 366));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(n - 1L);
        LocalDate toExclusive = today.plusDays(1);

        Map<LocalDate, Integer> active = new HashMap<>();
        for (DateCountRow r : mapper.countDailyActiveUsers(from, toExclusive)) {
            active.put(r.getStatDate(), r.getCount());
        }
        Map<LocalDate, Integer> launches = new HashMap<>();
        for (DateCountRow r : mapper.countDailyAppLaunches(from, toExclusive)) {
            launches.put(r.getStatDate(), r.getCount());
        }
        // 빈 날도 0 으로 채워 그래프가 끊기지 않게
        List<DailyAccessResponse> out = new ArrayList<>(n);
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            out.add(new DailyAccessResponse(d, active.getOrDefault(d, 0), launches.getOrDefault(d, 0)));
        }
        return out;
    }

    public List<HourCountRow> getHourlyAccess(LocalDate date) {
        AuthUtils.requireAdmin();
        Map<Integer, Integer> byHour = new HashMap<>();
        for (HourCountRow r : mapper.countHourlyActiveUsers(date)) {
            byHour.put(r.getHour(), r.getCount());
        }
        List<HourCountRow> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            HourCountRow r = new HourCountRow();
            r.setHour(h);
            r.setCount(byHour.getOrDefault(h, 0));
            out.add(r);
        }
        return out;
    }

    public List<SignupWeekResponse> getWeeklySignups(int weeks) {
        AuthUtils.requireAdmin();
        List<SignupWeekResponse> rows = new ArrayList<>(mapper.findWeeklySignups(Math.max(1, Math.min(weeks, 104))));
        rows.sort(Comparator.comparing(SignupWeekResponse::getStatWeek)); // 그래프는 오래된 주부터
        return rows;
    }

    public List<TrendingPostResponse> getTrending(int hours, boolean onlyTrending, int limit) {
        AuthUtils.requireAdmin();
        return mapper.findTrending(Math.max(1, Math.min(hours, 24 * 30)), onlyTrending,
                Math.max(1, Math.min(limit, 200)));
    }
}
