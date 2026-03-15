package com.back.auth.dto;

import lombok.Data;

@Data
public class PasswordResetRequest {
    private String loginId;
    private String newPassword;
}