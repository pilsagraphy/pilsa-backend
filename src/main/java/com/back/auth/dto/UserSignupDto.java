package com.back.auth.dto;

import lombok.Data;

@Data
public class UserSignupDto {
    private Long userId;
    private String loginId;
    private String password;
    private String passwordHash; // 서비스 단에서 암호화 후 세팅
    private String name;
    private String major;
    private String studentNo;
    private String email;
    private String role;
    private Boolean isDeleted;
}