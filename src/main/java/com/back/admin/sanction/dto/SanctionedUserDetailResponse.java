package com.back.admin.sanction.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SanctionedUserDetailResponse {
    // 회원 태그: caution(주의) / temporary(정지) / permanent(차단) / none
    private String tag;

    // 강제 탈퇴 (tag = 'withdrawn', PM 2026-10-17 "제재 회원 목록에 강퇴로") — loginId·name 은 가린 값
    private String withdrawReason;
    private LocalDateTime withdrawnAt;
    private String withdrawnByName;
    private String banStatus;
    private LocalDateTime bannedUntil;
    private LocalDateTime banStartedAt;

    // 누적주의: 현재 유효한 주의 포인트 합계 % caution_per_warning (다음 경고까지 남은 주의 아님, 스펙에 명시된 계산식 그대로)
    private Integer cautionRemainder;
    // 누적경고: 현재 유효한 경고 개수
    private Integer warningCount;
    // 신고가 수락(삭제 처리)되어 반영된 건수
    private Integer reportDeletedCount;
}
