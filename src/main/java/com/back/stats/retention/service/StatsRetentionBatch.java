package com.back.stats.retention.service;

import com.back.stats.access.mapper.StatsAccessMapper;
import com.back.stats.policy.StatsPolicy;
import com.back.stats.trending.mapper.StatsPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통계 원본 정리 배치 (일 1회 05:00 — 주간 가입 통계 04:50 다음 순번).
 *
 * stats_retention_days(기본 1825일=5년)가 지난 행을 <b>물리 삭제</b>한다.
 * 소프트삭제 대전제의 예외인 이유: 통계 행은 증적이 아니라 집계 재료이고, state 컬럼을 두면
 * 모든 집계 쿼리가 필터를 달고 다녀야 하는데 그 대가로 얻는 게 없다.
 *
 * stats_signup_weekly 는 지우지 않는다 — 연 52행이라 커지지 않고, 탈퇴로 사라진 가입 이력을
 * 대신하는 유일한 기록이라 오래된 행일수록 가치가 크다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsRetentionBatch {

    private final StatsAccessMapper statsAccessMapper;
    private final StatsPostMapper statsPostMapper;
    private final StatsPolicy statsPolicy;

    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    public void purgeExpiredStats() {
        int retentionDays = statsPolicy.statsRetentionDays();

        int accessRows = statsAccessMapper.deleteOlderThan(retentionDays);
        int postRows = statsPostMapper.deleteOlderThan(retentionDays);

        if (accessRows > 0 || postRows > 0) {
            log.info("통계 정리 배치 - 보존 {}일 경과: 접속 {}행, 게시글 집계 {}행 삭제",
                    retentionDays, accessRows, postRows);
        }
    }
}
