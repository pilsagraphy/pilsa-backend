package com.back.global.util;

/**
 * 목록 API 공통 페이지 파라미터 보정.
 *
 * 원래 admin.common.AdminServiceSupport 에만 있던 로직인데, 회원 화면(마이페이지 활동 목록,
 * 게시판 목록)도 같은 보정이 필요해 global 로 올렸다. admin 쪽은 이 클래스에 위임한다.
 *
 * 보정하지 않으면 두 가지 경로로 500 이 난다 — MySQL 은 음수 LIMIT/OFFSET 을 문법 오류로 거부한다.
 *  - ?size=-1        → LIMIT -1
 *  - ?page=30000000  → (page-1)*size 가 int 를 넘쳐 OFFSET 이 음수로 뒤집힘
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

    /**
     * OFFSET 계산. page 에 상한이 없어 큰 값이 오면 int 곱셈이 넘치므로 long 으로 계산한 뒤
     * 상한을 씌운다 — 데이터 범위를 넘은 페이지는 500 이 아니라 빈 목록이 된다.
     * (OFFSET 2147483647 은 MySQL 이 정상 수용한다)
     *
     * 여기서 size 는 1 미만만 막고 MAX_PAGE_SIZE 로 깎지 않는다. 깎으면 LIMIT 에 쓰는 size 와
     * 기준이 달라져(LIMIT 200 인데 OFFSET 은 100 기준) 페이지가 겹친다.
     * 상한을 둘지는 호출부가 clampSize 로 결정하고, 그 값을 그대로 넘겨야 한다.
     */
    public static int offset(int page, int size) {
        long offset = (long) (clampPage(page) - 1) * Math.max(1, size);
        return (int) Math.min(offset, Integer.MAX_VALUE);
    }
}
