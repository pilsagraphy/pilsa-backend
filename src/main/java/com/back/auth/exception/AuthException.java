package com.back.auth.exception;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AuthException extends BaseException {
  public AuthException(String message, HttpStatus status) {
    super(message, status);
  }
}