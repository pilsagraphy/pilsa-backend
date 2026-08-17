package com.back.admin.quote.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class QuoteException extends BaseException {
    public QuoteException(String message, HttpStatus status) {
        super(message, status);
    }
}
