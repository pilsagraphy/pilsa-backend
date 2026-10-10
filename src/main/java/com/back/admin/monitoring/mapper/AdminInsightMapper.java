package com.back.admin.monitoring.mapper;

import com.back.admin.monitoring.dto.BoardActivityResponse;
import com.back.admin.monitoring.dto.DateCountRow;
import com.back.admin.monitoring.dto.MemberActivityRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모니터링 고도화 조회 (PM 2026-10-10 밤) — 요약 카드 · 미접속 회원 · 게시판 활동 · 푸시 미등록 · 활동 상위.
 * 접속은 stats_access_hourly 하나에서 파생시킨다 (CLAUDE.md '통계는 원본 하나에서').
 */
@Mapper
public interface AdminInsightMapper {

    /** from 이후 인증 요청이 있던 서로 다른 회원 수 */
    int countActiveUsersSince(@Param("from") LocalDateTime from);

    int countMembers();

    int countSignupsSince(@Param("from") LocalDateTime from);

    int countPostsSince(@Param("from") LocalDateTime from);

    int countCommentsSince(@Param("from") LocalDateTime from);

    /** 처리 대기 신고 — 대상(글·댓글) 단위 */
    int countPendingReportTargets();

    int countPushRegisteredMembers();

    /** 마지막 접속(접속 기록 → 없으면 last_login_at)이 days 일보다 오래된 회원, 오래된 순 */
    List<MemberActivityRow> findInactiveMembers(@Param("days") int days);

    /** 푸시 기기를 하나도 등록하지 않은 회원, 최근 접속 순 */
    List<MemberActivityRow> findPushUnregisteredMembers();

    /** from 이후 글+댓글이 많은 회원 */
    List<MemberActivityRow> findTopMembers(@Param("from") LocalDateTime from, @Param("limit") int limit);

    List<BoardActivityResponse.BoardRow> findBoardActivity(@Param("from") LocalDateTime from);

    List<DateCountRow> countDailyPosts(@Param("from") LocalDate from);

    List<DateCountRow> countDailyComments(@Param("from") LocalDate from);
}
