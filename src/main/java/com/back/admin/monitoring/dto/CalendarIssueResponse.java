package com.back.admin.monitoring.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 캘린더 연동 장애 — 구글·카카오는 실패(FAILED) 매핑이 남은 회원, 네이버는 토큰 갱신 실패로 멈춘 회원,
 * 그리고 마지막 반영이 staleDays 일 넘게 없는 연동. 화면은 issue·provider 로 rows 를 걸러 회원 목록을 보여 준다
 */
@Data
public class CalendarIssueResponse {
    private int staleDays;
    private int googleFailedMembers;
    private int kakaoFailedMembers;
    private int naverStuckMembers;
    private int staleLinks;
    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row {
        /** google | kakao | naver */
        private String provider;
        /** FAILED | TOKEN | STALE */
        private String issue;
        private Long userId;
        private String name;
        private String loginId;
        private String memberType;
        /** FAILED: 실패로 남은 일정 수 / 그중 재시도 상한까지 소진한 수 */
        private Integer failedCount;
        private Integer exhaustedCount;
        private String lastError;
        /** 마지막 반영 시각(구글·카카오 last_synced_at, 네이버 last_sent_at, 없으면 연동 시각) */
        private LocalDateTime lastReflectedAt;
    }
}
