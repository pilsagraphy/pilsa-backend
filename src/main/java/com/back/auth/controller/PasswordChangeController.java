package com.back.auth.controller;

import com.back.auth.dto.PasswordChangeRequest;
import com.back.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "마이페이지-비밀번호 변경", description = "로그인 상태에서 현재 비밀번호를 재확인하고 새 비밀번호로 변경한다. 비로그인 초기화(PUT /api/auth/password/reset)와는 별개 API다.")
public class PasswordChangeController {

    private final AuthService authService;

    @Operation(summary = "비밀번호 변경 (마이페이지, 로그인 상태)",
            description = """
                    마이페이지의 비밀번호 변경 화면에서 호출한다. **로그인 상태에서 현재 비밀번호를 재확인**하므로
                    토큰만 탈취해서는 비밀번호를 바꿀 수 없다.

                    ### 요청 예시
                    ```json
                    { "currentPassword": "oldpw1234!", "newPassword": "newpw5678@", "newPasswordConfirm": "newpw5678@" }
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"비밀번호가 변경되었습니다."}
                    ```
                    실패: 400 {"message":"현재 비밀번호가 일치하지 않습니다."}
                    실패: 400 {"message":"새 비밀번호가 새 비밀번호 확인과 일치하지 않습니다."}
                    실패: 400 {"message":"비밀번호는 문자, 숫자, 특수문자를 포함한 8~20자여야 합니다."} (policy_settings.signup_password_regex)
                    실패: 400 {"message":"새 비밀번호가 현재 비밀번호와 같습니다."}
                    실패: 401 (미인증)
                    """)
    @PatchMapping("/api/user/mypage/password/reset")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }
}
