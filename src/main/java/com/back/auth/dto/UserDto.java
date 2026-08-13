package com.back.auth.dto;

import lombok.Data;

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
}
