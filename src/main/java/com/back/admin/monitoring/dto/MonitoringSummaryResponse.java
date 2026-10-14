package com.back.admin.monitoring.dto;

import lombok.Data;

/**
 * 모니터링 맨 위 요약 카드 (PM 2026-10-10 밤 "보통의 커뮤니티가 필요한 내용").
 * 접속은 stats_access_hourly(로그인 요청이 있던 시간대) 기준 — DAU 는 오늘, WAU 는 최근 7일, MAU 는 최근 30일의 서로 다른 회원 수.
 */
@Data
public class MonitoringSummaryResponse {
    private int dau;
    private int wau;
    private int mau;
    private int membersTotal;
    private int signupsThisMonth;
    private int postsLast7;
    private int commentsLast7;
    private int postsLast30;
    private int commentsLast30;
    /** 처리 대기 신고 — 대상(글·댓글) 단위 */
    private int pendingReports;
    /** 푸시 수신 기기를 하나라도 등록한 회원 수 */
    private int pushRegisteredMembers;

    // 간편로그인·캘린더 연동 현황 (2026-10-13)
    /** 간편로그인을 하나라도 연결한 회원 수 */
    private int socialLinkedMembers;
    /** 제공자별 연결 수 { GOOGLE, NAVER, KAKAO } — 한 회원이 여럿 연결하면 각각 센다 */
    private java.util.Map<String, Integer> socialByProvider;
    /** 비밀번호 없는 소셜 가입 회원 수 */
    private int socialOnlyMembers;
    /** 캘린더를 하나라도 연동한 회원 수 */
    private int calendarLinkedMembers;
    /** 캘린더 종류별 연동 수 { google, naver, kakao } */
    private java.util.Map<String, Integer> calendarByProvider;
}
