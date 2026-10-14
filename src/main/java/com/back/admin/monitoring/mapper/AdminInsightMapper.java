package com.back.admin.monitoring.mapper;

import com.back.admin.monitoring.dto.BoardActivityResponse;
import com.back.admin.monitoring.dto.CalendarIssueResponse;
import com.back.admin.monitoring.dto.CohortRetentionRow;
import com.back.admin.monitoring.dto.ContentReactionResponse;
import com.back.admin.monitoring.dto.HealthSignalResponse;
import com.back.admin.monitoring.dto.DateCountRow;
import com.back.admin.monitoring.dto.MemberActivityRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /** 간편로그인 제공자별 연결 회원 수 [{ provider, cnt }] (탈퇴 제외) */
    List<Map<String, Object>> countSocialByProvider();

    /** 간편로그인을 하나라도 연결한 회원 수 (탈퇴 제외) */
    int countSocialLinkedMembers();

    /** 비밀번호 없는 소셜 가입 회원 수 */
    int countSocialOnlyMembers();

    /** 캘린더 종류별 연동 회원 수 [{ provider: google|naver|kakao, cnt }] (탈퇴 제외) */
    List<Map<String, Object>> countCalendarByProvider();

    /** 캘린더를 하나라도 연동한 회원 수 (탈퇴 제외) */
    int countCalendarLinkedMembers();

    /** 마지막 접속(접속 기록 → 없으면 last_login_at)이 days 일보다 오래된 회원, 오래된 순 */
    List<MemberActivityRow> findInactiveMembers(@Param("days") int days);

    /** 푸시 기기를 하나도 등록하지 않은 회원, 최근 접속 순 */
    List<MemberActivityRow> findPushUnregisteredMembers();

    /** from 이후 글+댓글이 많은 회원 */
    List<MemberActivityRow> findTopMembers(@Param("from") LocalDateTime from, @Param("limit") int limit);

    List<BoardActivityResponse.BoardRow> findBoardActivity(@Param("from") LocalDateTime from);

    List<DateCountRow> countDailyPosts(@Param("from") LocalDate from);

    List<DateCountRow> countDailyComments(@Param("from") LocalDate from);

    /** from(월요일 0시) 이후 가입자를 가입 주별로 묶은 1·2·4주차 잔존, 최근 주부터 */
    List<CohortRetentionRow> findCohortRetention(@Param("from") LocalDateTime from);

    /** from 이후 정상 글의 게시판별 댓글(정상·공개)·좋아요·무댓글 글 수 */
    List<ContentReactionResponse.BoardRow> findContentReaction(@Param("from") LocalDateTime from);

    /** from 이후 정상 글마다 첫 댓글(글쓴이 제외)까지 걸린 분 — 댓글 없는 글은 빠진다 */
    List<ContentReactionResponse.FirstCommentRow> findFirstCommentMinutes(@Param("from") LocalDateTime from);

    /** 구글·카카오 FAILED 매핑이 남은 회원 (탈퇴 제외) */
    List<CalendarIssueResponse.Row> findCalendarFailedMembers(@Param("maxRetry") int maxRetry);

    /** 네이버 토큰 갱신 실패로 멈춘 회원 */
    List<CalendarIssueResponse.Row> findNaverStuckMembers();

    /** 마지막 반영이 days 일 넘게 없는 연동 (연동 단위) */
    List<CalendarIssueResponse.Row> findStaleCalendarLinks(@Param("days") int days);

    /** 기타 장애 신호 — 접속 기록·급상승·주간 가입 배치의 마지막 흔적과 캘린더 상한 소진 회원 수 */
    HealthSignalResponse findHealthSignals(@Param("maxRetry") int maxRetry);
}
