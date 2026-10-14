package com.back.admin.monitoring.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 기타 장애 신호 (PM 2026-10-15 "기타 장애 보고 모니터링") — 지금 DB 에 남는 흔적만 모은다.
 * 푸시·메일 발송 실패, 5xx·예외, 정리 배치 실행 이력은 DB 에 기록이 없어 여기 없다(로그 확인)
 */
@Data
public class HealthSignalResponse {
    /** 마지막 접속 기록 시간대 — 접속자가 있는데 멈추면 JWT 필터의 기록 훅 장애 */
    private LocalDateTime lastAccessHour;
    /** 마지막 급상승 집계 행 — 적재 컷 미달 구간은 행이 없어 조용한 시간엔 늦어도 정상 */
    private LocalDateTime lastTrendingHour;
    /** 주간 가입 배치(매일 04:50)가 마지막으로 고정한 시각 */
    private LocalDateTime lastSignupCapturedAt;
    /** 구글·카카오 재시도 상한까지 실패해 자동 복구가 멈춘 회원 수 */
    private int calendarExhaustedMembers;
    /** 서버가 판정한 경고 — 접속 기록 3시간 넘게 없음 / 가입 배치 2일 넘게 없음 */
    private Boolean accessStale;
    private Boolean signupBatchStale;
}
