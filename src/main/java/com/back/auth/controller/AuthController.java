package com.back.auth.controller;

import com.back.auth.dto.*;
import com.back.auth.exception.AuthException;
import com.back.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.Cookie;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "인증(로그인·회원가입·계정찾기)",
        description = "로그인/로그아웃, 회원가입, 액세스·리프레시 토큰 관리, 아이디/이메일/비밀번호 찾기를 담당한다. 전부 비로그인 상태에서 호출하는 PUBLIC API다.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 로그인
    @Operation(
            summary = "로그인 (accessToken 반환 + refreshToken 쿠키)",
            description = """
                    로그인 페이지에서 아이디/비밀번호 제출 시 호출한다.
                    성공하면 accessToken을 본문으로 반환하고, refreshToken은 HttpOnly 쿠키로 내려간다.

                    ### 요청 예시
                    ```json
                    {"loginId":"hong","password":"pw1234"}
                    ```

                    ### 응답 예시
                    ```json
                    {"accessToken":"eyJhbGciOi...","userId":80,"memberType":"STUDENT","adminLevel":0,"refreshExp":1740000000}
                    ```

                    실패: 401 {"message":"아이디 또는 비밀번호가 올바르지 않습니다."}
                    실패: 401 {"message":"승인되지 않은 계정입니다."}
                    실패(정지): 403 {"message":"정지된 계정입니다.","banType":"temporary","bannedUntil":"2026-03-30T00:00:00"}
                    실패(차단): 403 {"message":"영구적으로 차단된 계정입니다.","banType":"permanent","bannedUntil":null}

                    ※ 정지/차단 사유는 message로, 해제 일시는 bannedUntil 필드로 내려간다 —
                    프론트가 "2026.03.30 00:00 부터 다시 로그인 할 수 있습니다"를 그릴 수 있다.
                    """
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest, // 쿠키 확인을 위해 필요함.
                                              HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(request, httpRequest, response));
    }

    // 로그아웃
    @Operation(
            summary = "로그아웃 (리프레시 토큰 삭제)",
            description = """
                    헤더의 로그아웃 버튼 클릭 시 호출한다. Redis의 리프레시 토큰을 삭제하고 refreshToken 쿠키를 만료시킨다. (1기 API)

                    ### 요청 예시
                    ```
                    POST /api/auth/token/logout
                    (본문 없음 — refreshToken 쿠키 사용)
                    ```

                    ### 응답 예시
                    ```
                    200 OK (본문 없음)
                    ```
                    """
    )
    @PostMapping("/token/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response,
                                       HttpServletRequest request) {
        authService.logout(response, request);
        return ResponseEntity.ok().build();
    }

    // 회원가입
    @Operation(
            summary = "회원가입",
            description = """
                    회원가입 페이지에서 가입 폼 제출 시 호출한다.
                    관리 권한(admin_level)은 가입으로 얻을 수 없다 — 항상 0으로 저장되며 승격은 관리자만 할 수 있다.

                    ### 요청 예시
                    ```json
                    {"name":"홍길동","phone":"010-1234-5678","major":"컴퓨터공학과","studentNo":"20201234",
                     "email":"hong@pilsa.co.kr","loginId":"hong","password":"pw1234","memberType":"STUDENT"}
                    ```
                    ※ memberType 미지정 시 STUDENT. ADMIN 등 임의 문자열은 400

                    ### 응답 예시
                    ```json
                    {"message":"회원가입이 완료되었습니다."}
                    ```

                    실패: 409 {"message":"이미 존재하는 아이디입니다."}
                    실패: 409 {"message":"이미 존재하는 이메일입니다."}
                    실패: 400 {"message":"유효하지 않은 회원 구분입니다. (STUDENT/ALUMNI)"}
                    """
    )
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok(Map.of("message", "회원가입이 완료되었습니다."));
    }

    // 회원가입용 - 아이디 & 이메일 중복 확인 API
    @Operation(
            summary = "아이디/이메일 중복 확인 (회원가입)",
            description = """
                    회원가입 페이지에서 아이디 또는 이메일 입력 후 '중복 확인' 버튼 클릭 시 호출한다.
                    email과 loginId 중 하나만 넘긴다. (1기 API — 실패 응답이 JSON 객체가 아니라 문자열 본문이다)

                    ### 요청 예시
                    ```
                    GET /api/auth/check?email=hong@pilsa.co.kr
                    GET /api/auth/check?loginId=hong
                    ```

                    ### 응답 예시
                    ```
                    200 OK (본문 없음 — 사용 가능)
                    ```

                    실패: 400 "이미 가입된 이메일 주소입니다."  (문자열 본문)
                    실패: 400 "이미 사용 중인 아이디입니다."  (문자열 본문)
                    실패: 400 "회원가입-중복확인 중 예기치 못한 에러가 발생했습니다."  (파라미터를 둘 다 안 준 경우)
                    """
    )
    @GetMapping("/check")
    public ResponseEntity<?> check(
            @Parameter(description = "중복 확인할 이메일 (loginId와 둘 중 하나만 전달)", example = "hong@pilsa.co.kr")
            @RequestParam(required = false) String email,
            @Parameter(description = "중복 확인할 로그인 아이디 (email과 둘 중 하나만 전달)", example = "hong")
            @RequestParam(required = false) String loginId
    ) {
        // 이메일 체크
        if (email != null && !email.isBlank()) {
            boolean exists = authService.existsByEmail(email);
            if (exists) {
                return ResponseEntity.badRequest().body("이미 가입된 이메일 주소입니다.");
            }
            return ResponseEntity.ok().build(); // 200
        }
        // 아이디 체크
        if (loginId != null && !loginId.isBlank()) {
            boolean exists = authService.existsByLoginId(loginId);
            if (exists) {
                return ResponseEntity.badRequest().body("이미 사용 중인 아이디입니다.");
            }
            return ResponseEntity.ok().build(); // 200,
        }
        return ResponseEntity.badRequest().body("회원가입-중복확인 중 예기치 못한 에러가 발생했습니다."); // 둘 다 없으면 400
    }

    // 리프레시 토큰을 가지고 있는 쿠키의 존재 여부 확인
    @Operation(
            summary = "리프레시 토큰 쿠키 존재 확인 (자동 로그인 판정)",
            description = """
                    앱 첫 진입 시 자동 로그인 여부를 판정하기 위해 refreshToken 쿠키의 존재만 확인한다.
                    (1기 API — 본문 없이 상태코드로만 응답한다)

                    ### 요청 예시
                    ```
                    POST /api/auth/token/refresh/validate
                    (본문 없음 — refreshToken 쿠키 사용)
                    ```

                    ### 응답 예시
                    ```
                    200 OK (본문 없음) → 쿠키 있음
                    204 No Content (본문 없음) → 쿠키 없음
                    ```
                    """
    )
    @PostMapping("/token/refresh/validate")
    public ResponseEntity<Void> check(HttpServletRequest request) {
        boolean exists = authService.checkRefreshToken(request);
        if (!exists) {
            return ResponseEntity.noContent().build(); // 204 → 쿠키 없음
        }
        return ResponseEntity.ok().build(); // 200 → 쿠키 있음
    }

    // 쿠키에서 리프레시 토큰 검사 (refreshToken 추출)
    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String requireRefreshToken(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null) {
            throw new AuthException("로그인 정보가 없습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);
        }
        return refreshToken;
    }

    // 리프레시토큰 연장(재발급) -> 로그인 시 수동 연장
    @Operation(
            summary = "리프레시 토큰 연장(재발급) - 로그인 유지 수동 연장",
            description = """
                    "로그인 유지" 등 사용자가 세션 연장을 선택했을 때 리프레시 토큰을 재발급해 유효기간을 연장한다.
                    정지/차단 계정은 로그인과 동일하게 403 + banType/bannedUntil로 거부된다.

                    ### 요청 예시
                    ```
                    POST /api/auth/token/refresh/extend
                    (본문 없음 — refreshToken 쿠키 사용)
                    ```

                    ### 응답 예시
                    ```json
                    {"accessToken":"eyJ...","userId":80,"memberType":"STUDENT","adminLevel":0,"refreshExp":1740000000}
                    ```

                    실패: 401 {"message":"로그인 정보가 없습니다. 다시 로그인해주세요."}
                    실패: 401 {"message":"Refresh token (로그인을 다시 해주세요.)"}
                    실패(정지/차단): 403 — 로그인과 동일하게 banType/bannedUntil 필드 포함
                    """
    )
    @PostMapping("/token/refresh/extend")
    public ResponseEntity<AuthResponse> extend(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.extend(requireRefreshToken(request), response));
    }

    // 엑세스 토큰 발급/재발급 (+ 리프레시 토큰 회전)
    @Operation(
            summary = "액세스 토큰 발급/재발급 (+ 리프레시 토큰 회전)",
            description = """
                    액세스 토큰이 만료됐을 때(또는 자동 로그인 직후) 새 액세스 토큰을 발급받는다.
                    재발급 때마다 refreshToken 쿠키도 새로 교체된다(sliding). 매번 DB에서 회원 상태를 다시 확인하므로 정지된 계정은 즉시 막힌다.

                    ### 요청 예시
                    ```
                    POST /api/auth/token/access/refresh
                    (본문 없음 — refreshToken 쿠키 사용)
                    ```

                    ### 응답 예시
                    ```json
                    {"accessToken":"eyJ...","userId":80,"memberType":"STUDENT","adminLevel":0,"refreshExp":1740000000}
                    ```

                    실패: 401 {"message":"로그인 정보가 없습니다. 다시 로그인해주세요."}
                    실패(정지/차단): 403 — 로그인과 동일하게 banType/bannedUntil 필드 포함
                    """
    )
    @PostMapping("/token/access/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.refresh(requireRefreshToken(request), response));
    }

    // 아이디 찾기 전용 - 이메일+인증번호 검증
    @Operation(
            summary = "아이디 찾기 - 이메일+인증번호 검증",
            description = """
                    아이디찾기 페이지에서 이메일로 받은 인증번호를 입력해 검증할 때 호출한다.
                    검증을 통과해야만 GET /api/auth/id/find 를 호출할 수 있다. (1기 API — 실패 응답이 JSON 객체가 아니라 문자열 본문이다)

                    ### 요청 예시
                    ```json
                    {"email":"hong@pilsa.co.kr","code":"123456"}
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"이메일 인증이 완료되었습니다."}
                    ```

                    실패: 4xx "사유 메시지"  (AuthException의 상태코드 + 문자열 본문)
                    """
    )
    @PostMapping("/id/verify")
    public ResponseEntity<?> verifyFindIdCode(@RequestBody FindIdVerifyRequest request) {
        try {
            authService.verifyFindIdCode(request);
            return ResponseEntity.ok(Map.of(
                    "message", "이메일 인증이 완료되었습니다."
            ));
        } catch (AuthException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getMessage());
        }
    }

    // 아이디 찾기 전용 - 인증 완료된 이메일에 한해 loginId 반환
    @Operation(
            summary = "아이디 찾기 - 인증 완료된 이메일로 아이디 조회",
            description = """
                    아이디찾기 페이지에서 이메일 인증(POST /api/auth/id/verify)을 통과한 뒤 호출한다.
                    인증이 완료된 이메일에 한해 loginId를 반환한다. (1기 API — 실패 응답이 JSON 객체가 아니라 문자열 본문이다)
                    2기 POST /api/auth/email/find(이메일 찾기)와 방향이 반대인 API다.

                    ### 요청 예시
                    ```
                    GET /api/auth/id/find?email=hong@pilsa.co.kr
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"아이디 조회 성공","loginId":"hong"}
                    ```

                    실패: 4xx "사유 메시지"  (AuthException의 상태코드 + 문자열 본문, 예: 미인증 이메일)
                    """
    )
    @GetMapping("/id/find")
    public ResponseEntity<?> findLoginId(
            @Parameter(description = "인증을 완료한 이메일 주소", example = "hong@pilsa.co.kr")
            @RequestParam String email) {
        try {
            String loginId = authService.findLoginIdAfterVerification(email);
            return ResponseEntity.ok(Map.of(
                    "message", "아이디 조회 성공",
                    "loginId", loginId
            ));
        } catch (AuthException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getMessage());
        }
    }

    // 이메일 찾기 - 학번+이름 일치 시 마스킹된 이메일 반환
    // (아이디/이메일/비번 다 잊어서 아이디 찾기용 이메일조차 모를 때 사용)
    @Operation(
            summary = "이메일 찾기 (학번+이름, 마스킹 반환)",
            description = """
                    이메일찾기 페이지에서 호출한다. 아이디/이메일/비밀번호를 모두 잊어 아이디 찾기용 이메일조차 모를 때,
                    학번+이름이 일치하면 마스킹된 이메일을 알려준다.

                    ### 요청 예시
                    ```json
                    {"studentNo":"2026010101","name":"홍길동"}
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"이메일 조회 성공","email":"ho**@pilsa.co.kr"}
                    ```
                    ※ email은 마스킹된 값으로 내려간다

                    실패: 400 {"message":"학번과 이름을 모두 입력해주세요."}
                    실패: 404 {"message":"입력하신 학번과 이름에 일치하는 회원 정보가 없습니다."}
                    """
    )
    @PostMapping("/email/find")
    public ResponseEntity<Map<String, String>> findEmail(@RequestBody FindEmailRequest request) {
        String maskedEmail = authService.findMaskedEmail(request.getStudentNo(), request.getName());
        return ResponseEntity.ok(Map.of(
                "message", "이메일 조회 성공",
                "email", maskedEmail
        ));
    }

    // 비밀번호 초기화 전 단계 - 아이디 이메일 유효성 확인
    @Operation(
            summary = "비밀번호 찾기 - 아이디+이메일 검증 후 인증번호 발송",
            description = """
                    비밀번호찾기 페이지에서 아이디와 이메일을 입력했을 때 호출한다.
                    두 값이 같은 회원을 가리키면 해당 이메일로 인증번호를 발송하고 만료까지 남은 초(expireTime)를 반환한다.
                    (1기 API — 실패 응답이 JSON 객체가 아니라 문자열 본문이다)

                    ### 요청 예시
                    ```
                    GET /api/auth/verification?loginId=hong&email=hong@pilsa.co.kr
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"인증번호를 발송했습니다.","expireTime":300}
                    ```
                    ※ expireTime은 인증번호 만료까지 남은 초

                    실패: 4xx "사유 메시지"  (AuthException의 상태코드 + 문자열 본문, 예: 아이디·이메일 불일치)
                    """
    )
    @GetMapping("/verification")
    public ResponseEntity<?> verification(
            @Parameter(description = "가입 시 사용한 로그인 아이디", example = "hong")
            @RequestParam String loginId,
            @Parameter(description = "가입 시 사용한 이메일 주소", example = "hong@pilsa.co.kr")
            @RequestParam String email
    ) {
        try {
            long expireTime = authService.verifyLoginIdAndEmailAndSendCode(loginId, email);
            return ResponseEntity.ok(Map.of(
                    "message", "인증번호를 발송했습니다.",
                    "expireTime", expireTime
            ));
        } catch (AuthException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getMessage());
        }
    }

    // 비밀번호 초기화
    @Operation(
            summary = "비밀번호 찾기 - 비밀번호 초기화",
            description = """
                    비밀번호찾기 페이지에서 인증을 마친 뒤 새 비밀번호를 제출할 때 호출한다.
                    로그인 상태의 비밀번호 변경(PATCH /api/user/mypage/password/reset)과는 별개의 API다.
                    (1기 API — 성공 시 본문이 없고, 실패 응답은 JSON 객체가 아니라 문자열 본문이다)

                    ### 요청 예시
                    ```json
                    {"loginId":"hong","newPassword":"newpw1234"}
                    ```

                    ### 응답 예시
                    ```
                    200 OK (본문 없음)
                    ```

                    실패: 4xx "사유 메시지"  (AuthException의 상태코드 + 문자열 본문)
                    """
    )
    @PutMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok().build(); // 200
        } catch (AuthException e) {
            // 사용자 예외
            return ResponseEntity.status(e.getStatus()).body(e.getMessage());
        }
    }
}
