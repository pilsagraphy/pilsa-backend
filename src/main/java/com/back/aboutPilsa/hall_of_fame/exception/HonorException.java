package com.back.aboutPilsa.hall_of_fame.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class HonorException extends BaseException {
    public HonorException(String message, HttpStatus status) {
        super(message, status); // 부모인 BaseException의 생성자 호출
    }
}