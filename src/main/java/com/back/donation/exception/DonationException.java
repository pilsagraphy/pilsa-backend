package com.back.donation.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class DonationException extends BaseException {
    public DonationException(String message, HttpStatus status) {
        super(message, status); // 부모인 BaseException의 생성자 호출
    }
}