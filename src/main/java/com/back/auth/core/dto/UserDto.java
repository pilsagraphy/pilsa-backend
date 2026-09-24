package com.back.auth.core.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDto {
    private Long userId;
    private String loginId;
    private String password;
    private String passwordHash;
    private String name;
    private String email;
    private String memberType;   // 회원 구분: STUDENT / ALUMNI
    private Integer adminLevel;   // 관리 권한 레벨: 0=일반, 1~3=관리자
    private Boolean isDeleted;
    private String banStatus;
    private LocalDateTime bannedUntil;
    // 세션 무효화용 — 비밀번호가 바뀌면 +1 되어 그 사용자의 기존 토큰이 전부 무효가 된다 (JwtUtil 참고)
    private Integer tokenVersion;
}
