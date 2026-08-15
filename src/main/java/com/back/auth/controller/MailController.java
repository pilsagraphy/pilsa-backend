package com.back.auth.controller;

import com.back.auth.exception.MailException;
import com.back.auth.service.MailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.back.auth.dto.*;

import java.util.Map;

@Tag(name = "이메일 인증번호",
        description = "회원가입·아이디 찾기 등에서 공용으로 쓰는 이메일 인증번호의 발송/검증/남은 유효시간 조회를 담당한다. 전부 비로그인 호출 가능한 PUBLIC API다.")
@Slf4j
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    // 1. 인증번호 발송
    @Operation(
            summary = "이메일 인증번호 발송",
            description = """
                    회원가입·아이디찾기 페이지에서 이메일 입력 후 '인증번호 발송' 버튼 클릭 시 호출한다.
                    입력한 이메일로 인증번호를 발송하고 유효시간(초)을 함께 반환한다.

                    ### 요청 예시
                    ```json
                    {"email":"hong@pilsa.co.kr"}
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"인증번호를 발송했습니다.","expireTime":300}
                    ```
                    ※ expireTime = 인증번호 유효시간(초)

                    실패: 400 {"message":"이메일을 입력해주세요."}
                    실패: 500 {"message":"인증번호 발송에 실패했습니다. 잠시 후 다시 시도해주세요."}
                    """
    )
    @PostMapping("/verification-code")
    public ResponseEntity<Map<String, Object>> sendVerifyCode(@RequestBody EmailRequest request) {
        if (request.getEmail() == null || request.getEmail().isEmpty()) {
            throw new MailException("이메일을 입력해주세요.", HttpStatus.BAD_REQUEST);
        }

        String email = request.getEmail();
        long expireTime = mailService.sendCode(email);

        // 발송 실패하거나 시간이 정상적으로 생성되지 않은 경우
        if (expireTime <= 0) {
            log.warn("인증번호 발송 실패 - 대상: {}", email);
            throw new MailException("인증번호 발송에 실패했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        log.info("인증번호 발송 성공 - 대상: {}, 만료시간: {}초", email, expireTime);
        return ResponseEntity.ok(Map.of(
                "message", "인증번호를 발송했습니다.",
                "expireTime", expireTime   // 인증번호 유효시간(초)
        ));
    }

    // 2. 인증번호 검사
    @Operation(
            summary = "이메일 인증번호 검증",
            description = """
                    회원가입·아이디찾기 페이지에서 이메일로 받은 인증번호를 입력해 확인할 때 호출한다.
                    불일치·만료는 200 + false가 아니라 400 + message로 내려간다(프론트가 사유를 그대로 표시).

                    ### 요청 예시
                    ```json
                    {"email":"hong@pilsa.co.kr","code":"123456"}
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"인증이 완료되었습니다.","verified":true}
                    ```

                    실패: 400 {"message":"이메일과 인증번호를 모두 입력해주세요."}
                    실패: 400 {"message":"인증번호가 일치하지 않거나 만료되었습니다."}
                    """
    )
    @PostMapping("/verification-code/verify")
    public ResponseEntity<Map<String, Object>> verifyCode(@RequestBody VerifyRequest request) {
        if (request.getEmail() == null || request.getEmail().isEmpty()
                || request.getCode() == null || request.getCode().isEmpty()) {
            throw new MailException("이메일과 인증번호를 모두 입력해주세요.", HttpStatus.BAD_REQUEST);
        }

        String email = request.getEmail();
        if (!mailService.verifyCode(email, request.getCode())) {
            log.warn("인증번호 불일치 또는 만료 - Email: {}", email);
            throw new MailException("인증번호가 일치하지 않거나 만료되었습니다.", HttpStatus.BAD_REQUEST);
        }
        log.info("인증번호 검증 성공 - Email: {}", email);
        return ResponseEntity.ok(Map.of(
                "message", "인증이 완료되었습니다.",
                "verified", true
        ));
    }

    // 3. 남은 시간 확인
    @Operation(
            summary = "인증번호 남은 유효시간 조회 (타이머)",
            description = """
                    인증번호 입력 화면의 카운트다운 타이머가 남은 시간을 동기화할 때 호출한다.
                    (1기 API — 성공 응답이 JSON 객체가 아니라 Long 원시값 단독이고, 실패는 본문 없이 상태코드만 내려간다)

                    ### 요청 예시
                    ```
                    GET /api/mail/verification-code/ttl?email=hong@pilsa.co.kr
                    ```

                    ### 응답 예시
                    ```
                    245
                    ```
                    ※ 남은 초를 Long 값 하나로 반환 (JSON 객체 아님)

                    실패: 400 (본문 없음 — email 파라미터 누락)
                    실패: 404 (본문 없음 — 인증번호가 없거나 이미 만료됨)
                    """
    )
    @GetMapping("/verification-code/ttl")
    public ResponseEntity<Long> getRemainingTime(
            @Parameter(description = "인증번호를 발송한 이메일 주소 (누락 시 400)", example = "hong@pilsa.co.kr")
            @RequestParam(required = false) String email) {
        if (email == null || email.isEmpty()) {
            log.warn("남은 시간 조회 실패 - 이메일 파라미터 누락");
            return ResponseEntity.badRequest().build(); // 값이 비어있으면 400 에러 상자를 보냄
        }

        long timeLeft = mailService.getRemainingTime(email);
        // 남은 시간이 없거나 이미 만료된 경우 (데이터를 찾을 수 없음)
        if (timeLeft <= 0) {
            log.warn("남은 시간 조회 실패 (데이터 없음) - Email: {}", email);
            return ResponseEntity.notFound().build();
        }
        log.info("남은 시간 조회 성공 - Email: {}, 남은시간: {}초", email, timeLeft);
        return ResponseEntity.ok(timeLeft);
    }
}
