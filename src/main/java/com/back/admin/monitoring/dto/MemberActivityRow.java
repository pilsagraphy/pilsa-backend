package com.back.admin.monitoring.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 회원 한 명의 활동 요약 행 — 미접속 회원 목록 · 푸시 미등록 회원 목록 · 활동 상위 회원이 같이 쓴다 */
@Data
public class MemberActivityRow {
    private Long userId;
    private String name;
    private String loginId;
    private String memberType;
    private Integer adminLevel;
    private LocalDateTime joinedAt;
    /** 마지막 접속(인증 요청이 있던 시간대). 기록이 전혀 없으면 null */
    private LocalDateTime lastAccessAt;
    /** 마지막 접속으로부터 며칠 지났나 (기록 없음이면 가입일 기준) */
    private Integer daysSince;
    private int postCount;
    private int commentCount;
    private int deviceCount;
}
