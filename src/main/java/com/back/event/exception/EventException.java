package com.back.event.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class EventException extends BaseException {
    public EventException(String message, HttpStatus status) {
        super(message, status); // 프로젝트 공통 기반인 BaseException의 생성자 호출
    }
}