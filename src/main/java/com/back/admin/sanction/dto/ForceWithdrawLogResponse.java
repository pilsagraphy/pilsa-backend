package com.back.admin.sanction.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 강제 탈퇴 이력 (GET /api/admin/sanctions/withdrawals) — 제재 회원 관리 화면 (PM 2026-10-17).
 * 대상은 가린 이름·아이디와 회원 번호로만 남는다 (탈퇴 즉시 원문 파기 원칙). 보관은 policy_settings.force_withdraw_log_days.
 */
@Data
public class ForceWithdrawLogResponse {
    private List<Row> items;
    private long totalCount;
    private int totalPages;
    private int page;
    private int size;

    @Data
    public static class Row {
        private Long logId;
        private Long userId;
        private String maskedName;
        private String maskedLoginId;
        private String memberType;
        private String reason;
        private Long withdrawnBy;
        private String withdrawnByName; // 처리한 관리자 이름 (그 관리자가 탈퇴했으면 '탈퇴한 회원')
        private LocalDateTime createdAt;
    }
}
