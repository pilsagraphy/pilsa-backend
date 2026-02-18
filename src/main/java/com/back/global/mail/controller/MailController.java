package com.back.global.mail.controller;

import com.back.global.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    // 전송받을 메일주소 입력 하드 코딩해서 메일 넣기 - 현재 여기로만 보낼수 있음
    private static final String MY_EMAIL = "nicky081719@gmail.com";

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