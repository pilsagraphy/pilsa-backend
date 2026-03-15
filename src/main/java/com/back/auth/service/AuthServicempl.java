package com.back.auth.service;

import com.back.auth.dto.*;
import com.back.auth.mapper.AuthMapper;
import com.back.auth.exception.AuthException;
import com.back.global.security.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;


import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServicempl implements AuthService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // 운영과 개발 구분
    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;

    // 리프레시 쿠키 설정 메서드
    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth/token");
        // 브라우저 창 닫아도 쿠키 유지 : 20분(리프레시 토큰 유지시간)
        // cookie.setMaxAge(60 * 20);
        response.addCookie(cookie);
    }

    // 로그인(소셜로그인은 후순위)
    public AuthResponse login(LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse response) {
        // DB 조회
        UserDto user = authMapper.findByLoginId(request.getLoginId());

        // 아이디/비밀번호 확인
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        // 승인된 사용자만 로그인 허용
        if (user.getIsDeleted() == true) {
            throw new AuthException("승인되지 않은 계정입니다.", HttpStatus.UNAUTHORIZED);
        }

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // 리프레시 토큰을 쿠키에 저장
        addRefreshTokenCookie(response, refreshToken);

        // Refresh Token exp 추출
        var claims = jwtUtil.validateRefreshToken(refreshToken);
        long refreshExp = claims.getExpiration().getTime();

        authMapper.updateLastLoginAt(request.getLoginId());

        return new AuthResponse(accessToken, user.getUserId(), user.getRole(), refreshExp);
    }

    // 로그아웃
    public void logout(HttpServletResponse response, HttpServletRequest request) {
        // 1) refreshToken 쿠키 제거
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setMaxAge(0); // 즉시 만료
        cookie.setPath("/api/auth/token");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        response.addCookie(cookie);

        // 2) 세션키로 닫기 (쿠키에서 값 읽고 jti 추출)
        try {
            String refreshToken = null;
            if (request.getCookies() != null) {
                for (Cookie c : request.getCookies()) {
                    if ("refreshToken".equals(c.getName())) {
                        refreshToken = c.getValue();
                        break;
                    }
                }
            }
//            if (refreshToken != null && !refreshToken.isBlank()) {
//                String sessionKey = jwtUtil.extractJti(refreshToken);
//                if (sessionKey != null) {
//                    int n = authMapper.updateLogoutLogBySession(sessionKey);
//                    if (n == 0) {
//                        log.debug("닫을 세션 로그가 없음 (이미 로그아웃 되었을 수 있음). sessionKey={}", sessionKey);
//                    }
//                } else {
//                    log.debug("refreshToken에서 jti를 추출할 수 없음 (만료/위조 가능). 이메일 기반 닫기는 생략.");
//                }
//            } else {
//                log.debug("refreshToken 쿠키 없음. 이메일 기반 닫기는 생략.");
//            }
        } catch (Exception e) {
            log.warn("로그아웃 로그 기록 실패: {}", e.getMessage(), e);
        }
    }

    // 회원가입
    @Transactional
    public void signup(SignupRequest request) {

        // 중복 아이디 확인
        if (authMapper.existsByLoginId(request.getLoginId())) {
            throw new AuthException("이미 존재하는 아이디입니다.", HttpStatus.CONFLICT);
        }

        // 중복 이메일 확인
        if (authMapper.existsByEmail(request.getEmail())) {
            throw new AuthException("이미 존재하는 이메일입니다.", HttpStatus.CONFLICT);
        }

        // 비밀번호 암호화
        String encodedPw = passwordEncoder.encode(request.getPassword());

        UserSignupDto user = new UserSignupDto();

        user.setName(request.getName());
        user.setMajor(request.getMajor());
        user.setStudentNo(request.getStudentNo());
        user.setEmail(request.getEmail());
        user.setLoginId(request.getLoginId());
        user.setPasswordHash(encodedPw);
        user.setRole(request.getRole());
        user.setIsDeleted(Boolean.FALSE);

        authMapper.insertUser(user);
    }

    // 회원가입 - 아이디 중복
    @Override
    public boolean existsByLoginId(String loginId) {
        return authMapper.existsByLoginId(loginId);
    }

    // 회원가입 - 이메일 중복 확인
    @Override
    public boolean existsByEmail(String email) {
        return authMapper.existsByEmail(email);
    }

    // 리프레시 토큰을 가지고 있는 쿠키의 존재 여부 확인
    public boolean checkRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isEmpty()) {
                    return true; // refreshToken 쿠키 존재
                }
            }
        }
        return false; // 없음
    }

    // 리프레시토큰 연장(재발급) -> 로그인 시 수동 연장
    public AuthResponse extend(String refreshToken, HttpServletResponse response) {
        try {
            // 1) 기존 토큰 검증 + oldJti 추출
            var claims = jwtUtil.validateRefreshToken(refreshToken);
            String loginId = claims.getSubject();

            // 2) DB에서 user 다시 조회
            UserDto user = authMapper.findByLoginId(loginId);
            if (user == null) {
                throw new AuthException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED);
            }
            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                throw new AuthException("탈퇴했거나 승인되지 않은 계정입니다.", HttpStatus.UNAUTHORIZED);
            }

            // 3) 새 토큰 발급
            String newAccessToken = jwtUtil.generateAccessToken(user);
            String newRefreshToken = jwtUtil.generateRefreshToken(user);

//            // 4) jti 회전 (열린 행만 대상)
//            String oldJti = jwtUtil.extractJti(refreshToken);
//            String newJti = jwtUtil.extractJti(newRefreshToken);
//            if (oldJti != null && newJti != null && !oldJti.equals(newJti)) {
//                try {
//                    authMapper.rotateSessionKey(oldJti, newJti);
//                } catch (Exception e) {
//                    // 회전 실패해도 세션 연장은 진행 (로그만 영향)
//                    log.warn("rotateSessionKey 실패 oldJti={} newJti={} : {}", oldJti, newJti, e.getMessage());
//                }
//            }

            // 5) 쿠키 교체
            addRefreshTokenCookie(response, newRefreshToken);

            // 6) 응답
            var newClaims = jwtUtil.validateRefreshToken(newRefreshToken);
            long refreshExp = newClaims.getExpiration().getTime();

            return new AuthResponse(newAccessToken, user.getUserId(), user.getRole(), refreshExp);

        } catch (ExpiredJwtException e) {
            // Refresh Token 자체가 만료된 경우
            // 정상종료이므로 사용자의 재로그인이 필요함
            log.info("Refresh token expired at {}", e.getClaims().getExpiration());
            throw new AuthException("Refresh token (로그인을 다시 해주세요.)", HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            // 비정상 종료
            log.warn("Invalid refresh token: {}", e.getMessage());
            throw new AuthException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
    }

    // 엑세스 토큰 발급/재발급
    public AuthResponse refresh(String refreshToken) {
        try {
            var claims = jwtUtil.validateRefreshToken(refreshToken);
            String loginId = claims.getSubject();

            // DB에서 user 다시 조회
            UserDto user = authMapper.findByLoginId(loginId);
            if (user == null) {
                throw new AuthException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED);
            }
            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                throw new AuthException("탈퇴했거나 승인되지 않은 계정입니다.", HttpStatus.UNAUTHORIZED);
            }

            // 기존 refreshToken 그대로 쓰므로 exp도 동일
            long refreshExp = claims.getExpiration().getTime();
            String newAccessToken = jwtUtil.generateAccessToken(user);

            return new AuthResponse(newAccessToken, user.getUserId(), user.getRole(), refreshExp);

        } catch (ExpiredJwtException e) {
            // Refresh Token 자체가 만료된 경우
            // 정상종료이므로 사용자의 재로그인이 필요함
            log.info("Refresh token expired at {}", e.getClaims().getExpiration());
            throw new AuthException("Refresh token expired(로그인을 다시 해주세요.)", HttpStatus.UNAUTHORIZED);

        } catch (JwtException e) {
            // 비정상 종료
            log.warn("Invalid refresh token: {}", e.getMessage());
            throw new AuthException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
    }
}
//
//
//
//  // 비밀번호 재설정
//  public void resetPassword(PasswordResetRequest req) {
//    UserDto user = authMapper.findByEmail(req.getEmail());
//    if (user == null) {
//      throw new AuthException("가입된 이메일이 아닙니다.", HttpStatus.NOT_FOUND);
//    }
//
//    // 승인 Y / 대기 N / 탈퇴 X
//    if ("X".equals(user.getUserApproved())) {
//      throw new AuthException("계정이 비활성화되어 변경할 수 없습니다.", HttpStatus.GONE);
//    }
//
//    // 비밀번호 암호화 후 저장
//    String encoded = passwordEncoder.encode(req.getNewPassword());
//    authMapper.updatePassword(user.getUserId(), encoded);
//  }
//}