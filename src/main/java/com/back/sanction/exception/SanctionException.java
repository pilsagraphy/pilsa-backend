package com.back.sanction.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class SanctionException extends BaseException {

    public SanctionException(String message, HttpStatus status) {
        super(message, status);
    }
}
