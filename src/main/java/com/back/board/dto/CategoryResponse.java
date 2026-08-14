package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

// 카테고리 목록 (id + 이름). 공지사항은 카테고리가 없어 빈 목록이 반환된다.
@Getter
@Setter
public class CategoryResponse {
    private Long categoryId;
    private String name;
}
