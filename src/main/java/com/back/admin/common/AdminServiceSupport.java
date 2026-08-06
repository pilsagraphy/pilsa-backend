package com.back.admin.common;

import com.back.admin.common.exception.AdminException;
import com.back.global.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

// 관리자 서비스 공통 헬퍼 (인증 주체 추출, 일괄 실패 메시지, 페이지 파라미터 보정).
// 상태를 갖지 않는 정적 유틸.
public final class AdminServiceSupport {

    private static final int MAX_PAGE_SIZE = 100;

    private AdminServiceSupport() {
    }

    // 현재 로그인한 관리자 user_id (/api/admin/** 는 SecurityConfig 에서 ADMIN 으로 이미 제한됨)
    public static Long currentAdminId() {
        String subValue = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(subValue);
        } catch (NumberFormatException e) {
            throw new AdminException("로그인이 필요한 서비스입니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    // 일괄 처리 실패 사유 메시지 (도메인 예외는 그대로, 그 외는 일반 메시지)
    public static String resolveFailureMessage(Exception e) {
        return (e instanceof BaseException) ? e.getMessage() : "처리 중 오류가 발생했습니다.";
    }

    // 페이지 번호 보정 (1 미만 방지 → 음수 OFFSET 오류 예방)
    public static int clampPage(int page) {
        return Math.max(1, page);
    }

    // 페이지 크기 보정 (1 ~ MAX_PAGE_SIZE)
    public static int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
