package com.back.admin.common;

import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;

// 관리자 서비스 공통 헬퍼 (인증 주체 추출, 일괄 실패 메시지, 페이지 파라미터 보정, LIKE 이스케이프).
// 상태를 갖지 않는 정적 유틸. 관리자 화면(게시글·댓글·신고/제재·대시보드)이 공유한다.
public final class AdminServiceSupport {

    private static final int MAX_PAGE_SIZE = 100;

    private AdminServiceSupport() {
    }

    // 현재 로그인한 관리자 user_id (/api/admin/** 는 SecurityConfig 에서 ADMIN 으로 이미 제한됨)
    public static Long currentAdminId() {
        return AuthUtils.currentUserId();
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

    // LIKE 검색어 이스케이프: 사용자가 입력한 % _ \ 를 리터럴로 취급하도록 백슬래시 이스케이프.
    // 매퍼의 LIKE ... ESCAPE '\\' 와 짝을 이룬다. null/공백이면 null 반환(검색 미적용).
    public static String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.replace("\\", "\\\\")
                      .replace("%", "\\%")
                      .replace("_", "\\_");
    }
}
