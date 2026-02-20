package com.back.auth.service;

import com.back.auth.dto.AuthResponse;
import com.back.auth.dto.LoginRequest;
//import com.back.auth.dto.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response);
}
