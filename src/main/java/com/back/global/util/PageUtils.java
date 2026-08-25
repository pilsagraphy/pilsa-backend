package com.back.global.util;

/**
 * 목록 API 공통 페이지 파라미터 보정.
 *
 * 원래 admin.common.AdminServiceSupport 에만 있던 로직인데, 회원 화면(마이페이지 활동 목록)도
 * 같은 보정이 필요해 global 로 올렸다. admin 쪽은 이 클래스에 위임한다.
 *
 * size 를 보정하지 않으면 ?size=-1 이 그대로 LIMIT -1 로 나가 MySQL 문법 오류(=500)가 된다.
 */
public final class PageUtils {

    public static final int MAX_PAGE_SIZE = 100;

    private PageUtils() {
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
