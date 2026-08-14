package com.back.admin.common.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

// 관리자 공통 예외 (특정 도메인에 속하지 않는 관리자 공통 처리용)
public class AdminException extends BaseException {

    public AdminException(String message, HttpStatus status) {
        super(message, status);
    }
}
