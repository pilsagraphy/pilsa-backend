package com.back.auth.local.dto;

import lombok.Data;

@Data
public class PasswordResetRequest {
    private String loginId;
    private String newPassword;
}