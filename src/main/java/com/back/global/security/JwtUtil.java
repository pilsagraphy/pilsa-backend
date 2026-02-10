package com.back.global.security;

public class JwtUtil {
}

//package com.blue.global.security;
//
//import com.blue.auth.dto.UserDto;
//import io.jsonwebtoken.*;
//    import org.springframework.stereotype.Component;
//
//import java.util.Date;
//import java.util.UUID;
//
//@Component
//// 토큰 생성/검증 유틸
//public class JwtUtil {
//  private final JwtKeys jwtKeys;
//
//  // 생성자 주입
//  public JwtUtil(JwtKeys jwtKeys) {
//    this.jwtKeys = jwtKeys;
//  }
//
//  // 엑세스 토큰 발급
//  public String generateAccessToken(UserDto user) {
////    System.out.println("엑세스 토큰 발급");
//    // 엑세스 토큰 활성화 시간
//    long ACCESS_TOKEN_EXPIRATION = 1000L * 60 * 15; // 15분
//
//    return Jwts.builder()
//        // 토큰에 추가하고 싶은 정보
//        .setSubject(user.getUserEmail())
//        .claim("role", user.getUserRole())
//        .claim("name", user.getUserName())
//        .claim("isSuper", user.isSuper())
//        // 토큰 기본 정보
//        .setIssuedAt(new Date())
//        .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
//        .signWith(jwtKeys.accessKey(), SignatureAlgorithm.HS256)
//        .compact();
//  }
//
//  // 리프레시 토큰 발급
//  public String generateRefreshToken(UserDto user) {
////    System.out.println("리프레시 토큰 발급");
//    // 리프레시 토큰 활성화 시간
//    long REFRESH_TOKEN_EXPIRATION = 1000L * 60 * 60 * 12; // 12시간
//
//    return Jwts.builder()
//        .setId(UUID.randomUUID().toString())
//        // 토큰에 추가하고 싶은 정보
//        .setSubject(user.getUserEmail())
//        .claim("role", user.getUserRole())
//        .claim("name", user.getUserName())
//        .claim("isSuper", user.isSuper())
//        // 토큰 기본 정보
//        .setIssuedAt(new Date())
//        .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION))
//        .signWith(jwtKeys.refreshKey(), SignatureAlgorithm.HS256)
//        .compact();
//  }
//
//  // 엑세스 토큰 검증
//  public Claims validateAccessToken(String token) {
////    System.out.println("엑세스 토큰 검증");
//    return Jwts.parserBuilder()
//        .setSigningKey(jwtKeys.accessKey())
//        // 시간 오차 허용 (30초)
//        .setAllowedClockSkewSeconds(30)
//        .build()
//        .parseClaimsJws(token)
//        .getBody();
//  }
//
//  // 리프레시 토큰 검증
//  public Claims validateRefreshToken(String token) {
////    System.out.println("리프레시 토큰 검증");
//    return Jwts.parserBuilder()
//        .setSigningKey(jwtKeys.refreshKey())
//        // 시간 오차 허용 (30초)
//        .setAllowedClockSkewSeconds(30)
//        .build()
//        .parseClaimsJws(token)
//        .getBody();
//  }
//
//  // jti - UUID 추출 (없으면 null)
//  public String extractJti(String refreshToken) {
//    try {
//      Claims c = validateRefreshToken(refreshToken);
//      return c.getId(); // jti
//    } catch (JwtException e) {
//      return null;
//    }
//  }
//}