package com.back.mypage.notification.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class NotificationException extends BaseException {
    public NotificationException(String message, HttpStatus status) {
        super(message, status);
    }
}
