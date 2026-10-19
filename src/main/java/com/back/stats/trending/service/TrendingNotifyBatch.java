package com.back.stats.trending.service;

import com.back.mypage.notification.dto.NotificationType;
import com.back.mypage.notification.service.NotificationPolicy;
import com.back.mypage.notification.service.NotificationPublisher;
import com.back.stats.policy.mapper.StatsPolicyMapper;
import com.back.stats.trending.dto.TrendingNotifyPost;
import com.back.stats.trending.mapper.TrendingNotifyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주간 인기 글 알림 — 오래 안 들어온 회원에게 자유게시판의 이번 주 인기 글을 모아 보낸다 (PM 2026-10-10).
 * 흔한 커뮤니티 앱의 "돌아오세요" 알림. 화요일 10시(Asia/Seoul), 2주에 한 번 (trending_notify_interval_weeks, PM 2026-10-19).
 *
 * 수치는 전부 policy_settings:
 *   notify_trending (0/1, 운영 관리 > 알림 설정 스위치) · trending_notify_inactive_days (기본 7)
 *   trending_notify_board_name (기본 '자유게시판') · trending_notify_max_posts (기본 3)
 *
 * 글 고르기: 지난 7일 stats_post_hourly 에서 is_trending=1 이었던 글을 final_score 순으로. 하나도 없으면 지난 7일 반응
 * (조회+좋아요×5+댓글×3) 상위로 대신한다 — 조용한 주에도 "아무것도 없어서 안 보냄" 보다는 보내는 편이 목적(재유입)에 맞다.
 * 그래도 없으면(글이 없음) 건너뛴다.
 * 받는 사람: 탈퇴·차단이 아니고, 최근 trending_notify_active_within_days(기본 183)일 안에 접속한 적은 있지만
 * 최근 N일 동안은 접속하지 않은 회원 (PM 2026-10-11 "최근 반년 이내 접속했지만 최근 1주일 미접속"). 접속 기록이 없는 회원은 제외.
 * 회원 설정 스위치는 두지 않는다(PM) — NotificationPreferenceService.USER_TYPES 에 TRENDING 이 없어 항상 통과한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingNotifyBatch {

    private static final String INACTIVE_DAYS = "trending_notify_inactive_days";
    private static final String ACTIVE_WITHIN_DAYS = "trending_notify_active_within_days";
    private static final String BOARD_NAME = "trending_notify_board_name";
    private static final String MAX_POSTS = "trending_notify_max_posts";
    // 발송 주기 (PM 2026-10-19 "2주에 한번") — 배치는 매주 화요일에 깨어나되 기준일부터 interval 주가 되는 주에만 보낸다.
    // 기준일은 첫 발송일(2026-10-13). 재시작·재배포에도 같은 주를 고르도록 상태를 저장하지 않고 날짜로만 판정한다
    private static final String INTERVAL_WEEKS = "trending_notify_interval_weeks";
    private static final String ANCHOR_DATE = "trending_notify_anchor_date";
    private static final int DEFAULT_INTERVAL_WEEKS = 2;
    private static final String DEFAULT_ANCHOR_DATE = "2026-10-13";
    private static final int DEFAULT_INACTIVE_DAYS = 7;
    private static final int DEFAULT_ACTIVE_WITHIN_DAYS = 183; // 반년
    private static final String DEFAULT_BOARD_NAME = "자유게시판";
    private static final int DEFAULT_MAX_POSTS = 1; // 1편만 — 누르면 바로 그 글로 (PM 2026-10-10)
    private static final int TITLE_MAX = 100;
    private static final int MESSAGE_MAX = 500;

    private final TrendingNotifyMapper mapper;
    private final StatsPolicyMapper policyMapper;
    private final NotificationPolicy notificationPolicy;
    private final NotificationPublisher publisher;

    @Scheduled(cron = "0 0 10 * * TUE", zone = "Asia/Seoul")
    public void run() {
        if (!notificationPolicy.isEnabled(NotificationType.TRENDING)) {
            log.info("주간 인기 글 알림 — notify_trending 꺼짐, 건너뜀");
            return;
        }
        int intervalWeeks = intSetting(INTERVAL_WEEKS, DEFAULT_INTERVAL_WEEKS);
        LocalDate anchor = parseDate(stringSetting(ANCHOR_DATE, DEFAULT_ANCHOR_DATE));
        long weeks = ChronoUnit.WEEKS.between(anchor, LocalDate.now(ZoneId.of("Asia/Seoul")));
        if (weeks < 0 || weeks % intervalWeeks != 0) {
            log.info("주간 인기 글 알림 — {}주 주기라 이번 주는 건너뜀 (기준일 {}부터 {}주째)", intervalWeeks, anchor, weeks);
            return;
        }
        // 글은 지난 발송 이후 기간(주기×7일)에서 고른다
        int pickDays = intervalWeeks * 7;
        int inactiveDays = intSetting(INACTIVE_DAYS, DEFAULT_INACTIVE_DAYS);
        int activeWithinDays = Math.max(inactiveDays + 1, intSetting(ACTIVE_WITHIN_DAYS, DEFAULT_ACTIVE_WITHIN_DAYS));
        int maxPosts = intSetting(MAX_POSTS, DEFAULT_MAX_POSTS);
        String boardName = stringSetting(BOARD_NAME, DEFAULT_BOARD_NAME);

        Long boardId = mapper.findBoardIdByName(boardName);
        if (boardId == null) {
            log.warn("주간 인기 글 알림 — 게시판 '{}' 이 없어 건너뜀", boardName);
            return;
        }
        List<TrendingNotifyPost> posts = mapper.findTrendingPosts(boardId, maxPosts, pickDays);
        if (posts.isEmpty()) posts = mapper.findTopReactedPosts(boardId, maxPosts, pickDays);
        if (posts.isEmpty()) {
            log.info("주간 인기 글 알림 — 지난주 글이 없어 건너뜀 (board={})", boardName);
            return;
        }
        List<Long> receivers = mapper.findInactiveUserIds(inactiveDays, activeWithinDays);
        if (receivers.isEmpty()) {
            log.info("주간 인기 글 알림 — 최근 {}일 안에 접속했지만 {}일 이상 미접속인 회원 없음", activeWithinDays, inactiveDays);
            return;
        }

        TrendingNotifyPost first = posts.get(0);
        String title = truncate("[" + boardName + "] 이번 주 인기 글", TITLE_MAX);
        String message = truncate(posts.size() == 1
                ? first.getTitle()
                : posts.stream().map(p -> "· " + p.getTitle()).collect(Collectors.joining("\n")), MESSAGE_MAX);

        int sent = 0;
        for (Long receiverId : receivers) {
            try {
                // 알림함에는 쌓지 않는다 — OS 푸시만, 누르면 그 글로 (PM 2026-10-10)
                publisher.pushOnly(receiverId, NotificationType.TRENDING, "post", first.getPostId(), boardId, title, message);
                sent++;
            } catch (Exception e) {
                log.warn("주간 인기 글 알림 발행 실패 - userId: {}, {}", receiverId, e.getMessage());
            }
        }
        log.info("주간 인기 글 알림 — 글 {}편, 최근 {}일 접속·{}일 미접속 {}명 중 {}명 발송", posts.size(), activeWithinDays, inactiveDays, receivers.size(), sent);
    }

    private int intSetting(String code, int fallback) {
        try {
            String v = policyMapper.findPolicySetting(code);
            return v == null || v.isBlank() ? fallback : Math.max(1, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String stringSetting(String code, String fallback) {
        String v = policyMapper.findPolicySetting(code);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static LocalDate parseDate(String v) {
        try {
            return LocalDate.parse(v);
        } catch (Exception e) {
            return LocalDate.parse(DEFAULT_ANCHOR_DATE);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
