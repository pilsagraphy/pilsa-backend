package com.back.sanction.dto;

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
}
