package com.back.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    //private String accessToken;
    private String userId;
    private String role;
}
