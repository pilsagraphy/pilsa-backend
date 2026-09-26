package com.back.admin.monitoring.mapper;

import com.back.admin.monitoring.dto.AppLaunchDateRow;
import com.back.admin.monitoring.dto.AppLaunchMemberRow;
import com.back.admin.monitoring.dto.DateCountRow;
import com.back.admin.monitoring.dto.HourCountRow;
import com.back.admin.monitoring.dto.SignupWeekResponse;
import com.back.admin.monitoring.dto.TrendingPostResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 운영 관리 > 모니터링 — 관리 화면 조회 전용 (통계 원본은 stats.* 가 쌓는다) */
@Mapper
public interface AdminMonitoringMapper {

    /** 점검 대상 회원 전원 + 그날의 앱 실행 · 웹 접속 (탈퇴 · 영구차단 제외) */
    List<AppLaunchMemberRow> findMembersWithLaunch(@Param("date") LocalDate date);

    /** 기간 안의 (회원, 앱 실행일) 전부 — 연속 미접속 계산용 */
    List<AppLaunchDateRow> findLaunchDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 회원별로 기준일 이전(포함)에 마지막으로 앱을 연 날 */
    List<AppLaunchDateRow> findLastLaunchDates(@Param("date") LocalDate date);

    /** 앱 실행 기록의 첫 날 (없으면 null) */
    LocalDate findTrackingSince();

    /** 일별 활성 회원 수 (stats_access_hourly, 브라우저 + 앱) */
    List<DateCountRow> countDailyActiveUsers(@Param("from") LocalDate from, @Param("toExclusive") LocalDate toExclusive);

    /** 일별 앱 실행 회원 수 (app_launch_daily) */
    List<DateCountRow> countDailyAppLaunches(@Param("from") LocalDate from, @Param("toExclusive") LocalDate toExclusive);

    /** 하루의 시간대별 활성 회원 수 */
    List<HourCountRow> countHourlyActiveUsers(@Param("date") LocalDate date);

    /** 최근 N주 신규 가입 스냅샷 (최신 주부터) */
    List<SignupWeekResponse> findWeeklySignups(@Param("weeks") int weeks);

    /** 최근 N시간의 급상승 집계 행 (최신 구간 · 순위순) */
    List<TrendingPostResponse> findTrending(@Param("hours") int hours, @Param("onlyTrending") boolean onlyTrending,
                                            @Param("limit") int limit);
}
