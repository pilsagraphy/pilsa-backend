package com.back.admin.post.support;

import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;

// 게시글 관리 서비스 공통 헬퍼 (인증 주체 추출, 일괄 실패 메시지, 페이지 파라미터 보정).
// 상태를 갖지 않는 정적 유틸. 이름은 도메인별로 유일해야 한다 —
// mybatis.type-aliases-package=com.back 가 단순 클래스명으로 별칭을 등록하므로 중복명은 기동 실패를 유발한다.
public final class PostAdminSupport {

    private static final int MAX_PAGE_SIZE = 100;

    private PostAdminSupport() {
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
}
