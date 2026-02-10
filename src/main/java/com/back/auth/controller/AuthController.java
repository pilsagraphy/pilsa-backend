package com.back.auth.controller;

public class AuthController {
}

//package com.blue.auth.controller;
//
//import com.blue.auth.dto.*;
//    import com.blue.auth.service.AuthService;
//import com.blue.global.exception.AuthException;
//import jakarta.servlet.http.Cookie;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.web.bind.annotation.*;
//
//    import java.util.Map;
//
//@RestController
//@RequestMapping("/api/auth")
//@RequiredArgsConstructor
//public class AuthController {
//  private final AuthService authService;
//
//  @PostMapping("/login")
//  public ResponseEntity<?> login(@RequestBody LoginRequest request,
//                                 HttpServletRequest httpRequest,
//                                 HttpServletResponse response) {
//    try {
//      AuthResponse authResponse = authService.login(request, httpRequest, response);
//      return ResponseEntity.ok(authResponse);
//    }  catch (AuthException e) {
//      // 사용자 예외
//      return ResponseEntity.status(e.getStatus()).body(e.getMessage());
//    } catch (RuntimeException e) {
//      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//          .body("로그인 중 예기치 못한 에러가 발생했습니다.");
//    }
//  }
//
//  // 공통 메소드 : /refresh, /extend
//  // 쿠키에서 refreshToken 추출
//  private String extractRefreshToken(HttpServletRequest request) {
//    if (request.getCookies() != null) {
//      for (Cookie cookie : request.getCookies()) {
//        if ("refreshToken".equals(cookie.getName())) {
//          return cookie.getValue();
//        }
//      }
//    }
//    return null;
//  }
//
//  // 엑세스 토큰의 재발급
//  @PostMapping("/token/refresh")
//  public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
//    String refreshToken = extractRefreshToken(request);
//    if (refreshToken == null) {
//      return ResponseEntity.status(401).build();
//    }
//    return ResponseEntity.ok(authService.refresh(refreshToken));
//  }
//
//  // 로그인 수동 연장 (리프레시 토큰의 재발급)
//  @PostMapping("/token/extend")
//  public ResponseEntity<AuthResponse> extend(HttpServletRequest request, HttpServletResponse response) {
//    String refreshToken = extractRefreshToken(request);
//    if (refreshToken == null) {
//      return ResponseEntity.status(401).build();
//    }
//    return ResponseEntity.ok(authService.extend(refreshToken, response));
//  }
//
//  // 리프레시 토큰을 가지고 있는 쿠키의 존재 여부 확인
//  @PostMapping("/token/check")
//  public ResponseEntity<Void> check(HttpServletRequest request) {
//    boolean exists = authService.checkRefreshToken(request);
//    if (!exists) {
//      return ResponseEntity.noContent().build(); // 204 → 쿠키 없음
//    }
//    return ResponseEntity.ok().build(); // 200 → 쿠키 있음
//  }
//
//  // 로그아웃
//  @PostMapping("/token/logout")
//  public ResponseEntity<Void> logout(HttpServletResponse response,
//                                     HttpServletRequest request) {
//    authService.logout(response, request);
//    return ResponseEntity.ok().build();
//  }
//
//  // 이메일 중복 확인 API
//  @GetMapping("/check-email")
//  public ResponseEntity<Boolean> checkEmail(@RequestParam String email) {
//    boolean exists = authService.checkEmailDuplicate(email);
//    return ResponseEntity.ok(exists); // true = 중복, false = 사용 가능
//  }
//
//  // 회원가입
//  @PostMapping("/signup")
//  public ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request) {
//    return ResponseEntity.ok(authService.signup(request));
//  }
//
//  // 비밀번호 재설정
//  @PostMapping("/password/reset")
//  public ResponseEntity<?> resetPassword(@RequestBody PasswordResetRequest req) {
//    try {
//      authService.resetPassword(req);
//      return ResponseEntity.ok().build();
//    } catch (AuthException e) {
//      return ResponseEntity.status(e.getStatus()).body(e.getMessage());
//    }
//  }
//}