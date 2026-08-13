package com.back.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private Long userId;
    private String memberType;   // STUDENT / ALUMNI
    private Integer adminLevel;   // 0=일반, 1~3=관리자
    private long refreshExp;
}
