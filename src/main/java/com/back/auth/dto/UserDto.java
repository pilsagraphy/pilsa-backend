package com.back.auth.dto;

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
    private String role;
    private Boolean isDeleted;
    private String banStatus;
    private LocalDateTime bannedUntil;
}
