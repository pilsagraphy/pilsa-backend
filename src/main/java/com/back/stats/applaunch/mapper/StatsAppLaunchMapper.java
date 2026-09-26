package com.back.stats.applaunch.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 설치형 앱(TWA) 실행 기록 — app_launch_daily (회원·날짜당 1행).
 *
 * 접속 통계(stats_access_hourly)는 브라우저든 앱이든 인증된 요청이면 다 찍히므로 "앱으로 열었는지"를 가를 수 없다.
 * 앱 시작 URL(/?launch=app)로 들어온 세션이 프론트에서 하루 한 번 알려 주는 것을 여기 적는다.
 */
@Mapper
public interface StatsAppLaunchMapper {

    /** 오늘(DB 날짜) 행이 없으면 만들고, 있으면 마지막 실행 시각과 횟수만 올린다. */
    void recordLaunch(@Param("userId") Long userId);
}
