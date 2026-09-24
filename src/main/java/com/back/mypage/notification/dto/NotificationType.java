package com.back.mypage.notification.dto;

// 알림 유형. DB(notifications.type)에는 이 이름 그대로 저장된다.
public enum NotificationType {
    COMMENT("새 댓글이 달렸습니다."),
    REPLY("내 댓글에 답글이 달렸습니다."),
    REPORT_RESOLVED("신고하신 내용이 처리되었습니다."),
    SANCTION("운영정책 위반으로 조치가 적용되었습니다."),
    NOTICE("새 공지사항이 등록되었습니다."),
    // '중요' 로 올라온 글 — 그 게시판을 열람할 수 있는 회원 전원에게 (PM, 2026-09-21)
    PINNED_POST("중요 글이 올라왔습니다."),
    // 새 일정 등록 — 회원 전원에게 (PM, 2026-09-21)
    EVENT("새 일정이 등록되었습니다.");

    private final String defaultTitle;

    NotificationType(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}
