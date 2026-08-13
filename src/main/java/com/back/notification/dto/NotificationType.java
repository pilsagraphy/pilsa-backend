package com.back.notification.dto;

// 알림 유형. DB(notifications.type)에는 이 이름 그대로 저장된다.
public enum NotificationType {
    COMMENT("새 댓글이 달렸습니다."),
    REPLY("내 댓글에 답글이 달렸습니다."),
    REPORT_RESOLVED("신고하신 내용이 처리되었습니다."),
    SANCTION("운영정책 위반으로 조치가 적용되었습니다."),
    NOTICE("새 공지사항이 등록되었습니다.");

    private final String defaultTitle;

    NotificationType(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}
