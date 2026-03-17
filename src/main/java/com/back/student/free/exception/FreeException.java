package com.back.student.free.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class FreeException extends BaseException {

    public FreeException(String message, HttpStatus status) {
        super(message, status);
    }
}