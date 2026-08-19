package com.back.global.security;

import com.back.auth.dto.UserDto;
import io.jsonwebtoken.*;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

// 각종 토큰 생성 및 검증 유틸
@Component
public class JwtUtil {
    private final JwtKeys jwtKeys;

    // 생성자 주입
    public JwtUtil(JwtKeys jwtKeys) {
        this.jwtKeys = jwtKeys;
    }

    // 엑세스 토큰 발급 : 30분
    public String generateAccessToken(UserDto user) {
        // 엑세스 토큰 활성화 시간
        long ACCESS_TOKEN_EXPIRATION = 1000L * 60 * 30; // 30분

        return Jwts.builder()
                // 토큰에 추가하고 싶은 정보
                .setSubject(user.getLoginId())
                .claim("id", user.getUserId())
                .claim("memberType", user.getMemberType())
                .claim("adminLevel", user.getAdminLevel())
                .claim("name", user.getName())
                .claim("ver", versionOf(user))
                // 토큰 기본 정보
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(jwtKeys.accessKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /** 자동 로그인이 아닐 때의 리프레시 토큰 유지 시간 (12시간) */
    private static final long DEFAULT_REFRESH_EXPIRATION = 1000L * 60 * 60 * 12;

    // 리프레시 토큰 발급 (자동 로그인 아님 — 12시간)
    public String generateRefreshToken(UserDto user) {
        return generateRefreshToken(user, false, 0);
    }

    /**
     * 리프레시 토큰 발급.
     *
     * autoLogin 여부를 claim 으로 남기는 이유: 재발급(회전)할 때마다 새 토큰을 만드는데,
     * 원래 로그인이 자동 로그인이었는지 서버에 다른 저장소가 없다. claim 에 실어두면
     * 회전 시 그 값을 읽어 같은 수명을 승계할 수 있다 — 없으면 첫 재발급에서 12시간으로 깎여
     * 자동 로그인이 조용히 풀린다.
     *
     * @param autoLogin     자동 로그인 여부
     * @param autoLoginDays 자동 로그인일 때의 유지 일수 (policy_settings.auto_login_days)
     */
    public String generateRefreshToken(UserDto user, boolean autoLogin, int autoLoginDays) {
        long expiration = autoLogin
                ? 1000L * 60 * 60 * 24 * autoLoginDays
                : DEFAULT_REFRESH_EXPIRATION;

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                // 토큰에 추가하고 싶은 정보
                .setSubject(user.getLoginId())
                .claim("id", user.getUserId())
                .claim("memberType", user.getMemberType())
                .claim("adminLevel", user.getAdminLevel())
                .claim("name", user.getName())
                .claim("autoLogin", autoLogin)
                .claim("ver", versionOf(user))
                // 토큰 기본 정보
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(jwtKeys.refreshKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /** 리프레시 토큰의 autoLogin claim. 구 토큰(claim 없음)은 false 로 본다 */
    public boolean isAutoLogin(Claims refreshClaims) {
        return Boolean.TRUE.equals(refreshClaims.get("autoLogin", Boolean.class));
    }

    /**
     * 세션 무효화용 토큰 버전 (users.token_version).
     *
     * 리프레시 토큰이 무상태 JWT 라 발급 후에는 만료 전까지 취소할 수 없다. 그런데 자동 로그인은
     * 400일짜리라, 비밀번호를 바꿔도 탈취된 토큰이 그대로 살아 있는 구멍이 생긴다.
     * DB 의 숫자를 하나 올리면 그 사용자의 기존 토큰이 전부 무효가 되도록 대조용 값을 claim 에 싣는다.
     * (탈퇴·차단은 JwtAuthenticationFilter 가 매 요청 DB 를 보고 이미 즉시 막으므로 이 값과 무관하다)
     */
    private int versionOf(UserDto user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    /**
     * 토큰의 ver claim. 이 기능 도입 전에 발급된 구 토큰은 claim 이 없으므로 0 으로 본다 —
     * users.token_version 기본값도 0 이라 배포 시점에 살아 있던 세션이 끊기지 않는다.
     */
    public int tokenVersion(Claims claims) {
        Integer version = claims.get("ver", Integer.class);
        return version == null ? 0 : version;
    }

    // 엑세스 토큰 검증
    public Claims validateAccessToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtKeys.accessKey())
                // 시간 오차 허용 (60초)
                .setAllowedClockSkewSeconds(60)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 리프레시 토큰 검증
    public Claims validateRefreshToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtKeys.refreshKey())
                // 시간 오차 허용 (60초)
                .setAllowedClockSkewSeconds(60)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // jti - UUID 추출 (없으면 null)
    public String extractJti(String refreshToken) {
        try {
            Claims c = validateRefreshToken(refreshToken);
            return c.getId(); // jti
        } catch (JwtException e) {
            return null;
        }
    }
}