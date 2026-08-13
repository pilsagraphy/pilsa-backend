package com.back.admin.moderation.dto;

// 콘텐츠 표시 상태. DB에는 소문자 문자열(normal/blind/deleted)로 저장된다.
public enum ModerationState {
    NORMAL("normal"),
    BLIND("blind"),
    DELETED("deleted");

    private final String dbValue;

    ModerationState(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
