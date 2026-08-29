package com.back.auth.core;

import com.back.auth.core.dto.AuthResponse;
import com.back.auth.core.dto.UserDto;
import com.back.auth.core.exception.AuthException;
import com.back.auth.core.exception.BannedException;
import com.back.auth.core.mapper.AuthMapper;
import com.back.global.security.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 로그인 세션을 여는 단 하나의 지점.
 *
 * 아이디/비밀번호든 소셜이든, "이 사람이 맞다"까지 확인한 뒤에는 전부 여기를 지나간다.
 * 흩어져 있으면 어느 한 경로만 제재 확인을 빠뜨리는 사고가 나기 쉬운데,
 * 실제로 소셜 로그인이 자기 토큰을 직접 만들면 정지 계정이 구글로 우회해 들어올 수 있다.
 *
 * 신원 확인 방법(비밀번호 대조, 구글 id_token 검증)은 각 인증 수단이 하고,
 * 여기서는 그 이후만 책임진다 — 탈퇴·제재 확인, 토큰 발급, 리프레시 쿠키.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    /** policy_settings 행이 없거나 값이 깨졌을 때의 대비값 (브라우저 쿠키 만료 상한과 동일) */
    private static final int DEFAULT_AUTO_LOGIN_DAYS = 400;

    private final AuthMapper authMapper;
    private final JwtUtil jwtUtil;

    // 운영과 개발 구분
    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;

    /**
     * userId 만 아는 상태에서 세션을 연다 (소셜 로그인).
     * 조회 → 탈퇴·제재 확인 → 토큰 발급까지 한 번에 처리한다.
     */
    public AuthResponse issueForUserId(Long userId, boolean autoLogin, HttpServletResponse response) {
        UserDto user = authMapper.findByUserId(userId);

        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new AuthException("승인되지 않은 계정입니다.", HttpStatus.UNAUTHORIZED);
        }
        checkNotBanned(user);

        AuthResponse issued = issueFor(user, autoLogin, response);
        authMapper.updateLastLoginAtByUserId(userId);
        return issued;
    }

    /**
     * 이미 확인이 끝난 사용자에게 토큰을 발급하고 리프레시 쿠키를 심는다.
     * 탈퇴·제재 확인은 호출하는 쪽이 이미 마쳤다고 본다 (일반 로그인은 비밀번호 대조와 함께 처리한다).
     */
    public AuthResponse issueFor(UserDto user, boolean autoLogin, HttpServletResponse response) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user, autoLogin, autoLoginDays());
        addRefreshTokenCookie(response, refreshToken, autoLogin);

        long refreshExp = jwtUtil.validateRefreshToken(refreshToken).getExpiration().getTime();
        return new AuthResponse(accessToken, user.getUserId(), user.getMemberType(), user.getAdminLevel(), refreshExp);
    }

    /**
     * 리프레시 쿠키 설정.
     *
     * autoLogin=false 면 Max-Age 를 주지 않는다(세션 쿠키) — 브라우저를 완전히 닫으면 소멸한다.
     * autoLogin=true 면 policy_settings.auto_login_days 만큼 유지해 브라우저 재시작 후에도 복원되게 한다.
     * 쿠키 수명과 리프레시 토큰 만료를 같은 값으로 맞춘다 — 어긋나면 "쿠키는 있는데 토큰이 만료"거나
     * 그 반대가 되어 자동 로그인이 조용히 실패한다.
     */
    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken, boolean autoLogin) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth/token");
        if (autoLogin) {
            cookie.setMaxAge(autoLoginDays() * 24 * 60 * 60);
        }
        // autoLogin 이 아니면 Max-Age 미설정 = 세션 쿠키 (기존 동작 유지)
        response.addCookie(cookie);
    }

    /**
     * 리프레시 쿠키 제거 (로그아웃).
     * 지울 때도 Path·HttpOnly·Secure 가 심을 때와 같아야 브라우저가 같은 쿠키로 인식한다 —
     * 하나라도 어긋나면 새 쿠키가 하나 더 생길 뿐 기존 것은 남는다.
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setMaxAge(0); // 즉시 만료
        cookie.setPath("/api/auth/token");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        response.addCookie(cookie);
    }

    /**
     * 자동 로그인 유지 일수 (policy_settings.auto_login_days, 기본 400).
     * 400 이 상한인 이유: 브라우저가 쿠키 만료를 400일로 잘라내므로(Chrome 104+) 더 크게 줘도 의미가 없다.
     */
    public int autoLoginDays() {
        try {
            return Integer.parseInt(authMapper.findPolicySetting("auto_login_days"));
        } catch (Exception e) {
            return DEFAULT_AUTO_LOGIN_DAYS;
        }
    }

    /**
     * 정지/영구차단 계정 차단 (만료된 임시정지는 통과 — 스케줄러가 캐시를 정리하기 전이라도 로그인 허용).
     * 프론트가 "2026.03.30 00:00 부터 다시 로그인 할 수 있습니다" 화면을 그릴 수 있도록
     * 메시지 문자열이 아니라 banType/bannedUntil 필드로 내려준다.
     */
    public void checkNotBanned(UserDto user) {
        if ("permanent".equals(user.getBanStatus())) {
            throw new BannedException("영구적으로 차단된 계정입니다.", "permanent", null);
        }
        if ("temporary".equals(user.getBanStatus())
                && user.getBannedUntil() != null
                && user.getBannedUntil().isAfter(LocalDateTime.now())) {
            throw new BannedException("정지된 계정입니다.", "temporary", user.getBannedUntil());
        }
    }

    /**
     * 무효화된 리프레시 토큰인지 확인 (users.token_version 대조).
     *
     * 비밀번호가 바뀌면 token_version 이 올라가므로 그 전에 발급된 토큰은 여기서 걸린다.
     * 자동 로그인 토큰이 400일짜리라 이 관문이 없으면 비밀번호를 바꿔도 탈취된 세션을 끊을 수 없다.
     * (탈퇴·차단은 위 검사들과 JwtAuthenticationFilter 가 이미 즉시 막으므로 이 값과 무관하다)
     */
    public void checkTokenVersion(Claims claims, UserDto user) {
        int tokenVersion = jwtUtil.tokenVersion(claims);
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersion != currentVersion) {
            throw new AuthException("로그인 정보가 만료되었습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);
        }
    }
}
