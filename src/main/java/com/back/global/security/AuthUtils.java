package com.back.global.security;

import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 인증 주체(현재 로그인 사용자) 조회 공통 유틸.
 *
 * 각 도메인 서비스마다 흩어져 있던 getCurrentUserId()/checkAdminRole() 중복을 여기로 수렴한다.
 * 권한 판정은 URL 패턴이 아니라 "데이터(게시판 정책 등) + 이 유틸이 주는 사용자 속성"으로 한다.
 *  - memberType : STUDENT / ALUMNI (users.member_type)
 *  - adminLevel : 0=일반, 1~3=관리자 (users.admin_level)
 * 두 값 모두 JwtAuthenticationFilter가 매 요청 DB 최신값으로 심어준다.
 */
public final class AuthUtils {

    // JwtAuthenticationFilter가 부여하는 관리 레벨 권한 접두사 (예: ADMIN_LV_3)
    public static final String ADMIN_LEVEL_PREFIX = "ADMIN_LV_";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private AuthUtils() {
    }

    // 로그인하지 않았으면 예외
    public static Long currentUserId() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw new AuthorizationException("로그인이 필요한 서비스입니다.", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    // 비로그인 허용 API에서 사용 (없으면 null)
    public static Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            return null; // 익명 사용자(anonymousUser) 등
        }
    }

    public static boolean isLoggedIn() {
        return currentUserIdOrNull() != null;
    }

    // 관리자 여부 (admin_level >= 1)
    public static boolean isAdmin() {
        return hasAuthority(ROLE_ADMIN);
    }

    // 관리 레벨 (0=일반, 1~3=관리자). 게시판 작성권한(write_level) 판정에 사용
    public static int adminLevel() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return 0;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            if (value.startsWith(ADMIN_LEVEL_PREFIX)) {
                try {
                    return Integer.parseInt(value.substring(ADMIN_LEVEL_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    // 회원 구분 (STUDENT / ALUMNI). 미로그인이면 null → 게시판 열람권한(read_scope) 판정에 사용
    public static String memberType() {
        if (hasAuthority("ROLE_STUDENT")) {
            return "STUDENT";
        }
        if (hasAuthority("ROLE_ALUMNI")) {
            return "ALUMNI";
        }
        return null;
    }

    // 관리자가 아니면 예외 (관리자 전용 기능의 방어적 확인)
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new AuthorizationException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    // 최소 관리 레벨 요구 (예: 게시판 생성은 레벨 2 이상 등 정책이 생길 때)
    public static void requireAdminLevel(int minLevel) {
        if (adminLevel() < minLevel) {
            throw new AuthorizationException("관리 레벨 " + minLevel + " 이상만 사용할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }

    private static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    // 인증/인가 실패 공통 예외 (GlobalExceptionHandler가 {message} JSON으로 변환)
    public static class AuthorizationException extends BaseException {
        public AuthorizationException(String message, HttpStatus status) {
            super(message, status);
        }
    }
}
