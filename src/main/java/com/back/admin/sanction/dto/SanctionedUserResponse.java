package com.back.admin.sanction.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SanctionedUserResponse {
    private Long userId;
    private String loginId;
    private String name;
    private String email;
    private String banStatus;
    private LocalDateTime bannedUntil;
    private LocalDateTime banStartedAt;

    // 회원 태그: caution(주의) / temporary(정지) / permanent(차단)
    private String tag;

    // 강제 탈퇴 (tag = 'withdrawn', PM 2026-10-17 "제재 회원 목록에 강퇴로") — loginId·name 은 가린 값
    private String withdrawReason;
    private LocalDateTime withdrawnAt;
    private String withdrawnByName;
}
