package com.back.auth.local.controller;

import com.back.auth.local.dto.WithdrawRequest;
import com.back.auth.local.service.AuthService;
import com.back.auth.local.service.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "인증(로그인·회원가입·계정찾기)")
public class WithdrawController {

    private final WithdrawService withdrawService;
    private final AuthService authService;

    @Operation(summary = "회원 탈퇴",
            description = """
                    마이페이지의 [회원 탈퇴]에서 현재 비밀번호를 재입력받아 호출한다. 제재 여부와 무관하게 항상 가능.

                    처리 내용:
                    - 이름·이메일·아이디·전화번호·비밀번호 **즉시 파기** (이름은 "탈퇴한 회원"으로 표기됨)
                    - 학번은 복원 불가능한 해시로 치환 보관 — 부정 이용(제재 회피 재가입) 방지 목적
                    - 작성한 글·댓글은 남되 작성자가 "탈퇴한 회원"으로 표시됨
                    - 알림 수신 기기 해제, 로그인 세션 종료(refreshToken 쿠키 만료)

                    재가입: 일반 탈퇴자는 같은 학번으로 재가입 가능. 영구차단 상태로 탈퇴했으면 영구 거부,
                    정지 중 탈퇴했으면 정지 종료일까지 거부(회원가입 API 가 판정).

                    ### 요청 예시
                    ```json
                    {"password": "현재 비밀번호"}
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"탈퇴 처리되었습니다."}
                    ```
                    실패: 400 {"message":"비밀번호가 일치하지 않습니다."}
                    실패: 401 {"message":"..."} (미인증)
                    """)
    @PatchMapping("/api/user/mypage/withdraw")
    public ResponseEntity<Map<String, String>> withdraw(@RequestBody WithdrawRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        withdrawService.withdraw(request);
        // 세션 종료 — refreshToken 쿠키 만료 (기존 로그아웃 로직 재사용)
        authService.logout(httpResponse, httpRequest);
        return ResponseEntity.ok(Map.of("message", "탈퇴 처리되었습니다."));
    }
}
