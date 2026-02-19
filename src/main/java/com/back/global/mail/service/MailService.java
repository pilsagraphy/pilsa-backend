package com.back.global.mail.service;

public interface MailService {
    long sendCode(String email); // 인증번호 발송
    boolean verifyCode(String email, String code); // 인증번호 검증
    long extendTime(String email); // 시간 연장 (1번만, 3분으로 다시 리셋)
    long getRemainingTime(String email); // 남은 시간 확인
}