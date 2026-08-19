package com.back.auth.service;

import com.back.auth.dto.AuthResponse;
import com.back.auth.dto.LoginRequest;
//import com.back.auth.dto.SignupRequest;
import com.back.auth.dto.PasswordResetRequest;
import com.back.auth.dto.SignupRequest;
import com.back.auth.dto.FindIdVerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {
    // 로그인
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response);
    // 로그아웃
    void logout(HttpServletResponse response, HttpServletRequest request);
    //회원가입
    void signup(SignupRequest request);
    // 회원가입 - 아이디 & 이메일 중복 확인
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);

    // 아이디 찾기 전용 - 이메일+인증번호 검증
    void verifyFindIdCode(FindIdVerifyRequest request);
    // 아이디 찾기 전용 - 인증 완료된 이메일에 한해 loginId 반환
    String findLoginIdAfterVerification(String email);
    // 비밀번호 초기화 전 단계 - 아이디/이메일 확인 후 인증번호 발송
    long verifyLoginIdAndEmailAndSendCode(String loginId, String email);
    // 비밀번호 초기화
    void resetPassword(PasswordResetRequest request);

    // 이메일 찾기 - 학번/이름 일치 시 마스킹된 이메일 반환
    String findMaskedEmail(String studentNo, String name);

    // 리프레시 토큰을 가지고 있는 쿠키의 존재 여부 확인
    boolean checkRefreshToken(HttpServletRequest request);
    // 리프레시토큰 연장(재발급) -> 로그인 시 수동 연장
    AuthResponse extend(String refreshToken, HttpServletResponse response);
    // 엑세스 토큰 발급/재발급 (+ 리프레시 토큰 회전)
    AuthResponse refresh(String refreshToken, HttpServletResponse response);
    // 모든 기기에서 로그아웃 (users.token_version 을 올려 기존 토큰 전부 무효화)
    void logoutAllDevices(HttpServletRequest request, HttpServletResponse response);
}
