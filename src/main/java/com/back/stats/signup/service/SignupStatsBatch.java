package com.back.stats.signup.service;

import com.back.stats.policy.StatsPolicy;
import com.back.stats.signup.dto.SignupWeekRow;
import com.back.stats.signup.mapper.StatsSignupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 주간 신규가입 통계 배치 (일 1회 04:50 — 제재 캐시 04:00 → 탈퇴 행 04:30 → 알림 04:40 다음 순번).
 *
 * 최근 signup_stats_recalc_weeks(기본 2)주를 users 에서 <b>다시</b> 집계해 UPSERT 한다.
 * 다시 집계하는 이유는 진행 중인 주가 계속 늘고, 지난주 막바지 가입이 배치 시점에 따라 누락될 수 있기 때문이다.
 * 재집계 구간을 2주로 잡으면 하루 배치가 한 번 실패해도 다음 날 실행이 그 주를 메운다.
 *
 * 이미 지나간 주의 행은 손대지 않는다 — 그 값이 탈퇴로 사라진 원본을 대신하는 유일한 기록이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignupStatsBatch {

    private final StatsSignupMapper statsSignupMapper;
    private final StatsPolicy statsPolicy;

    @Scheduled(cron = "0 50 4 * * *")
    @Transactional
    public void captureWeeklySignups() {
        int recalcWeeks = statsPolicy.signupStatsRecalcWeeks();
        // 이번 주 월요일에서 (재집계 주 - 1)주를 뺀 월요일부터 — recalcWeeks=2 면 지난주 월요일
        LocalDate fromWeekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(recalcWeeks - 1L);

        List<SignupWeekRow> rows = statsSignupMapper.aggregateSignupsSince(fromWeekStart);
        if (rows.isEmpty()) {
            log.info("주간 가입 통계 - {} 이후 가입 없음 (기존 행 유지)", fromWeekStart);
            return;
        }

        statsSignupMapper.upsertWeeklySignups(rows);
        log.info("주간 가입 통계 완료 - {} 이후 {}주 재집계 (총 가입 {}명)",
                fromWeekStart, rows.size(), rows.stream().mapToInt(SignupWeekRow::getSignupCount).sum());
    }
}
