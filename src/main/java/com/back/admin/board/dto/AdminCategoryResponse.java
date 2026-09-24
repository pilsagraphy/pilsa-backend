package com.back.admin.board.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 게시판 관리 화면의 카테고리 한 줄.
 *
 * 회원 화면용 CategoryResponse 와 달리 관리에 필요한 값을 더 얹는다 —
 * 쓰고 있는 글 수(지우기 전에 알아야 한다)와 '중요'(PINNED) 여부(이 줄은 고칠 수 없다).
 */
@Getter
@Setter
public class AdminCategoryResponse {

    private Long categoryId;
    private String name;
    private String code;
    private Integer displayOrder;

    /** 이 카테고리를 달고 있는 살아있는 글 수. 0 이 아니면 삭제를 막는다. */
    private int postCount;

    /** 상단 고정에 쓰이는 '중요' 카테고리인가. true 면 이름 수정·삭제 불가. */
    private Boolean isPinned;
}
