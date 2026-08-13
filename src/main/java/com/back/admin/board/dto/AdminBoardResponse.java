package com.back.admin.board.dto;

import lombok.Getter;
import lombok.Setter;

// 게시판 관리 목록 한 행 (시안: 게시판 명 / 게시글 수 / 열람 권한 / 작성 권한)
@Getter
@Setter
public class AdminBoardResponse {
    private Long boardId;
    private String name;
    private int postCount;
    private String readScope;
    private Integer writeLevel;
    private Integer displayOrder;
    private Boolean allowComment;
    private Boolean allowAttachment;
    private Boolean categoryMode;
    private Long defaultCategoryId;
    private Boolean allowAnonymous;
    private Boolean allowPrivateComment;
}
