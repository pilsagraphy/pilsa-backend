package com.back.admin.user.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 관리자 회원 상세 (GET /api/admin/users/{userId}) — 회원 목록에서 이름을 눌러 들어오는 화면 (PM 2026-10-10).
 * 기본 정보 · 활동 수치 · 알림(기기·유형 설정·끈 글 수) · 구글 연동 · 제재 요약 · 최근 30일 접속/앱 실행 기록을 한 번에 준다.
 */
@Data
public class UserDetailResponse {
    // 기본
    private Long userId;
    private String loginId;
    private String name;
    private String phone;
    private String studentNo;
    private String email;
    private String major;
    private String memberType;
    private Integer adminLevel;
    private LocalDateTime joinedAt;
    private LocalDateTime lastLoginAt;
    private Boolean isDeleted;
    private String banStatus;
    private LocalDateTime bannedUntil;

    // 활동
    private int postCount;
    private int commentCount;
    private int likeGivenCount;     // 이 회원이 누른 좋아요
    private int likeReceivedCount;  // 이 회원 글이 받은 좋아요

    // 알림
    private int deviceCount;                        // 푸시 수신 기기 수 (0 이면 어디서도 푸시를 못 받는다)
    private Map<String, Boolean> notificationSettings; // COMMENT/REPLY/PINNED_POST/EVENT
    private int muteCount;                          // 알림을 끈 글·댓글 수

    // 구글 연동
    private Boolean googleLinked;
    private String googleEmail;
    private LocalDateTime googleLinkedAt;
    private LocalDateTime googleLastSyncedAt;

    // 제재 요약
    private int cautionPoints;     // 유효 주의 점수 합
    private int warningCount;      // 유효 경고 수
    private LocalDateTime lastBanStartAt;
    private LocalDateTime lastBanEndAt;

    // 접속 · 앱 실행 (최근 30일)
    private LocalDateTime lastAccessAt;
    private int accessDays30;
    private List<DailyCount> accessDaily;      // [{ date, count: 접속 시간대 수 }]
    private LocalDateTime lastAppLaunchAt;
    private int appLaunchDays30;
    private List<DailyCount> appLaunchDaily;   // [{ date, count: 실행 횟수 }]

    @Data
    public static class DailyCount {
        private LocalDate date;
        private int count;
    }
}
