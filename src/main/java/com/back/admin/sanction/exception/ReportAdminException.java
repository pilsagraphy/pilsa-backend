package com.back.admin.sanction.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ReportAdminException extends BaseException {

    public ReportAdminException(String message, HttpStatus status) {
        super(message, status);
    }
}
