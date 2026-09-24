package com.back.global.oauth;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

/** 구글 연동(소셜 로그인·캘린더) 처리 중 발생하는 예외. */
public class GoogleIntegrationException extends BaseException {
    public GoogleIntegrationException(String message, HttpStatus status) {
        super(message, status);
    }
}
