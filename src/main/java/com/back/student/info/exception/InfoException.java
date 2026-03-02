package com.back.student.info.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InfoException extends BaseException {

    public InfoException(String message, HttpStatus status) {
        super(message, status);
    }
}