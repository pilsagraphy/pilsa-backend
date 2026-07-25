package com.back.student.notice.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class NoticeException extends BaseException {
    public NoticeException(String message, HttpStatus status) {
        super(message, status); // 프로젝트 공통 기반인 BaseException의 생성자 호출
    }
}