package com.back.auth.local.dto;

import lombok.Data;

@Data
public class UserSignupDto {
    private Long userId;
    private String loginId;
    private String password;
    private String passwordHash; // 서비스 단에서 암호화 후 세팅
    private String name;
    private String phone;
    private String major;
    private String studentNo;
    private String email;
    private String memberType;   // 가입 시 회원 구분(기본 STUDENT)
    private Integer adminLevel;   // 가입 시 관리 레벨(기본 0)
    private Boolean isDeleted;
}