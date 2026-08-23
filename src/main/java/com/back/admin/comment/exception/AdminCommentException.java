package com.back.admin.comment.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AdminCommentException extends BaseException {

    public AdminCommentException(String message, HttpStatus status) {
        super(message, status);
    }
}
