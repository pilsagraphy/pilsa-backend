package com.back.admin.report.dto;

// 신고 처리 상태. DB(reports_log.status)에는 소문자 문자열로 저장된다.
public enum ReportStatus {
    PENDING("pending"),
    RESOLVED("resolved"),
    REJECTED("rejected");

    private final String dbValue;

    ReportStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
