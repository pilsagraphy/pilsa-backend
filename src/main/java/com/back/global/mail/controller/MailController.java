package com.back.global.mail.controller;

import com.back.global.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.back.global.mail.dto.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    // 하드코딩 테스트용 - API 연동 전까지만 사용
    private static final String MY_EMAIL = "nicky081719@gmail.com";

    // 1. 인증번호 발송
    @PostMapping("/verifycode") // 클라이언트가 이 경로로 POST보내면 메서드 실행
    public ResponseEntity<Long> sendVerifyCode(@RequestBody EmailRequest request) {
        String email = (request.getEmail() != null) ? request.getEmail() : MY_EMAIL;
        // 하드코딩 테스트용 메일 사용 가능하도록 론트 연동 전이라면 MY_EMAIL을 사용하고, 연동 후에는 전달받은 email을 사용
        long expireTime = mailService.sendCode(email);
        // 발송 실패하거나 시간이 정상적으로 생성되지 않은 경우
        if (expireTime <= 0) {
            // 데이터가 없거나 이미 만료되어 에러가 뜬다면  404 상자를 보냄
            log.warn("인증번호 발송 실패 - 대상: {}", email);
            return ResponseEntity.notFound().build();
        }
        log.info("인증번호 발송 성공 - 대상: {}, 만료시간: {}초", email, expireTime);
        return ResponseEntity.ok(expireTime); // 200 상태코드와 함께 남은 시간을 보내줌
    }

    // 2. 인증번호 검사
    @PostMapping("/verifycode/verify")
    public ResponseEntity<Boolean> verifyCode(@RequestBody VerifyRequest request) {
        String email = (request.getEmail() != null) ? request.getEmail() : MY_EMAIL;
        boolean isSuccess = mailService.verifyCode(email, request.getCode());
        if (!isSuccess) {
            log.warn("인증번호 불일치 또는 만료 - Email: {}", email);
            // 200 OK에 false를 담아 보냄. 로그만 찍음
        }
        log.info("인증번호 검증 결과: {}", isSuccess);
        return ResponseEntity.ok(isSuccess); // 일치하면 true, 틀렸다면 false를 반환
    }

    // 3. 남은 시간 확인
    @GetMapping("/verification-code/ttl")
    public ResponseEntity<Long> getRemainingTime(@RequestParam(required = false) String email) {
        String targetEmail = (email != null && !email.isEmpty()) ? email : MY_EMAIL;
        long timeLeft = mailService.getRemainingTime(targetEmail);
        // 남은 시간이 없거나 이미 만료된 경우 (데이터를 찾을 수 없음)
        if (timeLeft <= 0) {
            log.warn("남은 시간 조회 실패 (데이터 없음) - Email: {}", targetEmail);
            return ResponseEntity.notFound().build();
        }
        log.info("남은 시간 조회 성공 - Email: {}, 남은시간: {}초", targetEmail, timeLeft);
        return ResponseEntity.ok(timeLeft);
    }

    // ======= 테스트용 ======= 이거 없이 보는 법 모르겠음

    // 0. 메일 전송 테스트
    // http://localhost:8080/api/mail/test 입력
    @GetMapping("/test")
    public String hardCodingTest() {
        try {
            // 서비스 호출 (내부에서 난수생성 + 레디스저장 + 메일발송이 일어남)
            mailService.sendCode(MY_EMAIL);
            return "메일 발송 명령 성공! 메일함을 확인하세요.";
        } catch (Exception e) {
            return "실패 원인: " + e.getMessage();
        }
    }

    // 1. 남은 시간 확인 테스트
    // http://localhost:8080/api/mail/time 입력
    @GetMapping("/time")
    public String timeTest() {
        long remainingTime = mailService.getRemainingTime(MY_EMAIL);
        return "남은 시간: " + remainingTime + "초";
    }

    // 2. 시간 연장 테스트
    // http://localhost:8080/api/mail/extend 입력
    @GetMapping("/extend")
    public String extendTest() {
        long newTime = mailService.extendTime(MY_EMAIL);
        return newTime > 0 ? "시간 연장 성공! 새 남은 시간: " + newTime : "연장 실패 (이미 연장했거나 데이터 없음)";
    }

    // 3. 인증번호 검증 테스트
    // http://localhost:8080/api/mail/verify?code=인증번호 6자리 입력하고 테스트
    @GetMapping("/verify")
    public String verifyTest(@RequestParam String code) {
        boolean isSuccess = mailService.verifyCode(MY_EMAIL, code);
        return isSuccess ? "인증 성공! -레디스에서 삭제함" : "인증 실패! 번호를 확인하세요.";
    }
}