package com.back.schedule.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ScheduleException extends BaseException {
    public ScheduleException(String message, HttpStatus status) {
        super(message, status); // 프로젝트 공통 기반인 BaseException의 생성자 호출
    }
}