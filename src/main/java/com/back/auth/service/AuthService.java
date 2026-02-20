package com.back.auth.service;

import com.back.auth.dto.AuthResponse;
import com.back.auth.dto.LoginRequest;
//import com.back.auth.dto.SignupRequest;
import com.back.auth.dto.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {
    // 로그인
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response);
    //회원가입
    void signup(SignupRequest request);
    // 회원가입 - 아이디 & 이메일 중복 확인
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
