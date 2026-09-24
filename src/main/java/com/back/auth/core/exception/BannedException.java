package com.back.auth.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 정지/영구차단 계정의 인증 거부.
 *
 * 시안 p14/p15 의 "로그인이 제한된 계정입니다 / 2026.03.30 00:00 부터 다시 로그인 할 수 있습니다" 화면을
 * 프론트가 그대로 그릴 수 있도록, 해제 일시를 메시지 문자열이 아닌 별도 필드로 전달한다.
 *
 * bannedUntil 은 ISO 가 아니라 화면 표기 그대로 'yyyy.MM.dd HH:mm' 로 내린다 — 이 안내 화면이 쓰는 형태가
 * 하나뿐이라 프론트에 포맷 코드를 두지 않기 위함이다(FE 요청, 2026-08-18).
 * 관리자 화면(제재 목록·상세)은 표기가 달라 그쪽 DTO 는 ISO 를 유지한다 — 이 예외 경로 한정 규칙이다.
 */
@Getter
public class BannedException extends AuthException {

    private final String banType;             // temporary / permanent
    private final LocalDateTime bannedUntil;  // 영구차단이면 null

    /** 403 응답의 bannedUntil 표기 형식. 이 계약을 쓰는 곳(예외 핸들러·JWT 필터)이 공유한다 */
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    /** 영구차단(null)이면 null 그대로 — 프론트가 "기한 없음"으로 분기한다 */
    public static String formatBannedUntil(LocalDateTime bannedUntil) {
        return bannedUntil == null ? null : bannedUntil.format(DISPLAY_FORMAT);
    }

    public BannedException(String message, String banType, LocalDateTime bannedUntil) {
        super(message, HttpStatus.FORBIDDEN);
        this.banType = banType;
        this.bannedUntil = bannedUntil;
    }
}
