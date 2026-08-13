package com.back.user.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class UserException extends BaseException {

    public UserException(String message, HttpStatus status) {
        super(message, status);
    }
}
