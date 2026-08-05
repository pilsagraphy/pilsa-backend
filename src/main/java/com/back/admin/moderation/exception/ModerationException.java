package com.back.admin.moderation.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ModerationException extends BaseException {

    public ModerationException(String message, HttpStatus status) {
        super(message, status);
    }
}
