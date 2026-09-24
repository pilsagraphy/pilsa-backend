package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 게시판 목록 한 행 (사용자 화면용).
 * 관리자 화면용 AdminBoardResponse 와 달리 운영 정보(게시글 수 등)는 담지 않고,
 * 프론트가 메뉴를 그리고 작성 버튼 노출을 판단하는 데 필요한 것만 담는다.
 */
@Getter
@Setter
public class BoardSummaryResponse {
    private Long boardId;
    private String boardName;         // 한글 게시판명 (화면에 그대로 노출) — 응답 필드 boardName 컨벤션
    private Integer displayOrder;
    private Boolean canWrite;         // 현재 사용자가 글을 쓸 수 있는가 → 작성 버튼 노출 판단
    private Boolean allowComment;
    private Boolean allowAttachment;
    private Boolean categoryMode;     // 카테고리 선택 UI 노출 여부
    private Boolean allowAnonymous;   // 익명 체크박스 노출 여부
    private Boolean allowPrivateComment; // 비밀댓글 체크박스 노출 여부
}
