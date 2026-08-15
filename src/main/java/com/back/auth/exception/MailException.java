package com.back.auth.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 이메일 인증번호 처리 실패.
 * GlobalExceptionHandler 가 {"message": ...} JSON 으로 변환한다 (무본문 400/404 금지).
 */
public class MailException extends BaseException {
    public MailException(String message, HttpStatus status) {
        super(message, status);
    }
}
