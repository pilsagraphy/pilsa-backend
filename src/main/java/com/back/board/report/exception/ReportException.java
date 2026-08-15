package com.back.board.report.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ReportException extends BaseException {

    public ReportException(String message, HttpStatus status) {
        super(message, status);
    }
}
