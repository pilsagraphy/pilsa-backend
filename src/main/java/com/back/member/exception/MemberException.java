package com.back.member.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class MemberException extends BaseException {

    public MemberException(String message, HttpStatus status) {
        super(message, status);
    }
}
