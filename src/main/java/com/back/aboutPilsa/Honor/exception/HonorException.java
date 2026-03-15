package com.back.aboutPilsa.Honor.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class HonorException extends BaseException {
    public HonorException(String message, HttpStatus status) {
        super(message, status); // 부모인 BaseException의 생성자 호출
    }
}