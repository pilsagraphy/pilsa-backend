package com.back.admin.board.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 게시판 생성/수정 요청.
 * 수정 시 전달된 필드만 반영되므로 모든 필드가 nullable 이다.
 */
@Getter
@Setter
@ToString
public class BoardSaveRequest {
    private String name;                  // 게시판 이름 (한글, 중복 불가)
    private String readScope;             // MEMBER(재학+졸업) / STUDENT(재학) / ALUMNI(졸업) — 전체 공개(ALL) 불가
    private Integer writeLevel;           // 0=일반회원, 1~3=관리자 레벨
    private Integer displayOrder;         // 노출 순서 (미지정 시 맨 뒤)
    private Boolean allowComment;         // 댓글 사용
    private Boolean allowAttachment;      // 첨부 사용
    private Boolean categoryMode;         // 카테고리 사용
    private Long defaultCategoryId;       // 카테고리 기본값
    private Boolean allowAnonymous;       // 익명 작성 허용
    private Boolean allowPrivateComment;  // 비밀댓글 허용
}
