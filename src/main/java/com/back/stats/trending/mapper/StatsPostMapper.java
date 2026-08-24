package com.back.stats.trending.mapper;

import com.back.stats.trending.dto.PostStatHistory;
import com.back.stats.trending.dto.PostStatRow;
import com.back.stats.trending.dto.TrendingCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 구간 집계(stats_post_hourly) 쿼리.
 *
 * 후보·이력을 <b>목록 단위(IN)</b>로 한 번에 읽는다 — 글 수만큼 쿼리를 날리면 집계 주기가 짧아질 때
 * 곧바로 N+1 부하가 된다.
 */
@Mapper
public interface StatsPostMapper {

    /**
     * 집계 후보 — 살아 있는 글(state='normal') + 집계 대상 게시판(state='normal', trending_enabled=1)
     * + 글 나이가 maxAgeHours 이내.
     *
     * @param until 구간 종료 시각. 나이 기준과 "이 시점 이전에 작성된 글" 조건을 이 값으로 맞춰
     *              나중에 같은 구간을 재실행해도 후보 집합이 흔들리지 않게 한다.
     */
    List<TrendingCandidate> findCandidates(@Param("until") LocalDateTime until,
                                           @Param("maxAgeHours") int maxAgeHours);

    /**
     * 후보들의 직전 이력 (기준선 누적값 + 직전 N구간 raw_score 합).
     *
     * @param before  이번 구간 시작. 이 시점 <b>이전</b> 행만 본다(재실행 안전).
     * @param windows 기준선에 쓸 직전 구간 수
     */
    List<PostStatHistory> findHistories(@Param("postIds") List<Long> postIds,
                                        @Param("before") LocalDateTime before,
                                        @Param("windows") int windows);

    // 구간 집계 결과 적재. 같은 (stat_hour, post_id) 재실행 시 값만 갱신된다(멱등)
    void upsertPostStats(@Param("rows") List<PostStatRow> rows);

    // 보존기간 경과 행 물리 삭제
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
