package com.back.auth.local.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String name;
    private String phone;
    private String major;
    private String studentNo;
    private String email;
    private String loginId;
    private String password;
    private String memberType;   // 회원 구분: STUDENT / ALUMNI (미지정 시 서비스에서 STUDENT 기본)
}
