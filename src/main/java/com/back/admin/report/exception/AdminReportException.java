package com.back.admin.report.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AdminReportException extends BaseException {

    public AdminReportException(String message, HttpStatus status) {
        super(message, status);
    }
}
