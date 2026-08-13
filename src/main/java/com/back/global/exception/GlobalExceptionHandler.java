package com.back.global.exception;

import com.back.auth.exception.BannedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 처리기.
 *
 * 응답은 항상 JSON 객체다: { "message": "..." } (+ 예외별 부가 필드)
 * 프론트가 문자열을 파싱하지 않고 필드로 분기할 수 있게 하기 위함이다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 정지/차단 — 해제 일시를 필드로 내려 프론트가 "N월 N일부터 로그인 가능" 화면을 그릴 수 있게 한다
    @ExceptionHandler(BannedException.class)
    public ResponseEntity<Map<String, Object>> handleBanned(BannedException e) {
        log.debug("BannedException handled: {} ({})", e.getMessage(), e.getBanType());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", e.getMessage());
        body.put("banType", e.getBanType());          // temporary / permanent
        body.put("bannedUntil", e.getBannedUntil());  // 영구차단이면 null
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Map<String, Object>> handleBaseException(BaseException e) {
        log.debug("BaseException handled: {}", e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    // 미처리 예외는 내부 메시지를 노출하지 않는다 (스택은 서버 로그로만)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "서버 오류가 발생했습니다."));
    }
}
