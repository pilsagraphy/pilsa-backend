package com.back.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
// 전역 예외 처리기
public class GlobalExceptionHandler {
  
  @ExceptionHandler(AuthException.class)
  public ResponseEntity<String> handleAuthException(AuthException e) {
    log.debug("AuthException handled: {}", e.getMessage());
    return ResponseEntity.status(e.getStatus()).body(e.getMessage());
  }
}