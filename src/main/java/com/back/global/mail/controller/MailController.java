package com.back.global.mail.controller;

import com.back.global.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.back.global.mail.dto.*;

@Slf4j
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    // 1. 인증번호 발송
    @PostMapping("/verifycode") // 클라이언트가 이 경로로 POST보내면 메서드 실행
    public ResponseEntity<Long> sendVerifyCode(@RequestBody EmailRequest request) {
        if (request.getEmail() == null || request.getEmail().isEmpty()) {
            log.warn("인증번호 발송 실패 - 이메일이 입력되지 않음");
            return ResponseEntity.badRequest().build(); // 값이 비어있으면 400 에러 상자를 보냄
        }

        String email = request.getEmail();
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
        if (request.getEmail() == null || request.getEmail().isEmpty() ||
                request.getCode() == null || request.getCode().isEmpty()) {
            log.warn("인증 검증 실패 - 입력 데이터 누락");
            return ResponseEntity.badRequest().build(); // 값이 비어있으면 400 에러 상자를 보냄
        }

        String email = request.getEmail();
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