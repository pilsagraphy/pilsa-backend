package com.back.auth.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String name;
    private String major;
    private String studentNo;
    private String email;
    private String loginId;
    private String password;
    private String role;
}
