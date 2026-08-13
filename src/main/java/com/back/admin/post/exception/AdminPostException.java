package com.back.admin.post.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AdminPostException extends BaseException {

    public AdminPostException(String message, HttpStatus status) {
        super(message, status);
    }
}
