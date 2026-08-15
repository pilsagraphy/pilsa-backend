package com.back.global.exception;

import com.back.auth.exception.BannedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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

    // 요청 값 검증 실패(@Valid) → 400. 첫 위반 메시지를 그대로 내려 프론트가 폼에 표시할 수 있게 한다.
    // (없으면 NOT NULL 위반이 DB까지 내려가 500으로 나간다)
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
        log.debug("검증 실패: {}", message);
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    // 존재하지 않는 경로/정적 리소스는 404로 응답한다.
    // (catch-all 핸들러가 이걸 먼저 잡으면 오타난 URL이 500으로 나가 원인 파악이 어려워진다)
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception e) {
        log.debug("매핑되지 않은 요청: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "요청하신 경로를 찾을 수 없습니다."));
    }

    // 미처리 예외는 내부 메시지를 노출하지 않는다 (스택은 서버 로그로만)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "서버 오류가 발생했습니다."));
    }
}
