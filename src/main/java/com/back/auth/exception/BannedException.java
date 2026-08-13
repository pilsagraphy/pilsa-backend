package com.back.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * 정지/영구차단 계정의 인증 거부.
 *
 * 시안 p14/p15 의 "로그인이 제한된 계정입니다 / 2026.03.30 00:00 부터 다시 로그인 할 수 있습니다" 화면을
 * 프론트가 그대로 그릴 수 있도록, 해제 일시를 메시지 문자열이 아닌 별도 필드로 전달한다.
 */
@Getter
public class BannedException extends AuthException {

    private final String banType;             // temporary / permanent
    private final LocalDateTime bannedUntil;  // 영구차단이면 null

    public BannedException(String message, String banType, LocalDateTime bannedUntil) {
        super(message, HttpStatus.FORBIDDEN);
        this.banType = banType;
        this.bannedUntil = bannedUntil;
    }
}
