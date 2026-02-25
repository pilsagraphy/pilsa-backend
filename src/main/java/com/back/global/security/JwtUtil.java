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
                .claim("role", user.getRole())
                .claim("name", user.getName())
                // 토큰 기본 정보
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(jwtKeys.accessKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // 리프레시 토큰 발급
    public String generateRefreshToken(UserDto user) {
        // 리프레시 토큰 활성화 시간
        long REFRESH_TOKEN_EXPIRATION = 1000L * 60 * 60 * 12; // 12시간

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                // 토큰에 추가하고 싶은 정보
                .setSubject(user.getLoginId())
                .claim("id", user.getUserId())
                .claim("role", user.getRole())
                .claim("name", user.getName())
                // 토큰 기본 정보
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION))
                .signWith(jwtKeys.refreshKey(), SignatureAlgorithm.HS512)
                .compact();
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