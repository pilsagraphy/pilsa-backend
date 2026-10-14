package com.back.admin.monitoring.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 가입 주(월요일 기준) 코호트 한 줄. wN 은 가입일 기준 N주차 7일 창(가입일+7N ~ +7N+7)에 한 번이라도 접속한 회원 수.
 * eligible 은 그 창이 다 지난 회원 수 — 아직 창이 끝나지 않은 회원은 분모에서 뺀다(0 이면 화면은 '-')
 */
@Data
public class CohortRetentionRow {
    private LocalDate weekStart;
    private int signups;
    private int w1Eligible;
    private int w1Retained;
    private int w2Eligible;
    private int w2Retained;
    private int w4Eligible;
    private int w4Retained;
}
