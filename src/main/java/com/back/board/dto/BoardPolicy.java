package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 게시판 1건의 정책 (boards 테이블 1행).
 *
 * 예전에는 BoardType enum 이 boardId 1/2/3 을 하드코딩하고 있어서 관리자가 게시판을 새로 만들면
 * 코드 수정·재배포 전까지 동작하지 않았다. 이제 게시판 정책은 전부 이 DTO(=boards 행)로 온다.
 *
 *  - readScope  : 열람 대상 집합 MEMBER / STUDENT / ALUMNI  (신분 기준)
 *  - writeLevel : 작성에 필요한 최소 관리레벨 0~3            (관리레벨 기준)
 * 열람과 작성은 기준 축이 달라서 하나의 숫자로 합치지 않는다.
 *
 * 게시판에는 "전체 공개(ALL)"가 없다 — 어떤 게시판이든 최소한 로그인 회원이어야 열람할 수 있다.
 * 비로그인에게 열어야 하는 것은 게시판이 아니라 공개 리소스(/api/donations, /api/quotes/current, /api/event)다.
 */
@Getter
@Setter
public class BoardPolicy {

    public static final String SCOPE_MEMBER = "MEMBER";    // 로그인 회원 전체(재학생+졸업생)
    public static final String SCOPE_STUDENT = "STUDENT";  // 재학생 전용
    public static final String SCOPE_ALUMNI = "ALUMNI";    // 졸업생(동문) 전용

    private Long boardId;
    private String name;                  // 화면 노출 한글명 (예: 자유게시판)
    private String readScope;
    private Integer writeLevel;
    private Integer displayOrder;
    private Boolean allowComment;         // 댓글 허용
    private Boolean allowAttachment;      // 첨부 허용
    private Boolean categoryMode;         // 카테고리 사용
    private Long defaultCategoryId;       // 카테고리 미선택 시 기본값 (null이면 미사용)
    private Boolean allowAnonymous;       // 익명 작성 허용
    private Boolean allowPrivateComment;  // 비밀댓글 허용
    private String state;                 // normal / deleted

    // 첨부파일 저장 경로: 게시판별 폴더를 board_id 로 규칙 생성 (새 게시판도 설정 없이 동작)
    public String uploadDir() {
        return "uploads/board-" + boardId;
    }

    public boolean isCommentAllowed() {
        return Boolean.TRUE.equals(allowComment);
    }

    public boolean isAttachmentAllowed() {
        return Boolean.TRUE.equals(allowAttachment);
    }

    public boolean isCategoryUsed() {
        return Boolean.TRUE.equals(categoryMode);
    }

    public boolean isAnonymousAllowed() {
        return Boolean.TRUE.equals(allowAnonymous);
    }

    public boolean isPrivateCommentAllowed() {
        return Boolean.TRUE.equals(allowPrivateComment);
    }

    /**
     * 이 게시판을 열람할 수 있는가.
     * 관리자(레벨 1 이상)는 운영 목적상 readScope 와 무관하게 열람 가능.
     */
    public boolean canRead(String memberType, int adminLevel) {
        if (adminLevel >= 1) {
            return true;
        }
        if (memberType == null) {
            return false; // 비로그인은 어떤 게시판도 열람 불가 (ALL 스코프 폐지)
        }
        if (SCOPE_MEMBER.equals(readScope)) {
            return true; // 재학생 + 졸업생
        }
        return readScope != null && readScope.equals(memberType); // STUDENT / ALUMNI 정확히 일치
    }

    /**
     * 이 게시판에 글을 쓸 수 있는가. 열람 가능이 전제이며, 관리레벨이 writeLevel 이상이어야 한다.
     * writeLevel = 0 이면 로그인 회원 누구나.
     */
    public boolean canWrite(String memberType, int adminLevel) {
        if (!canRead(memberType, adminLevel)) {
            return false;
        }
        if (memberType == null) {
            return false; // 비로그인은 작성 불가
        }
        return adminLevel >= (writeLevel == null ? 0 : writeLevel);
    }
}
