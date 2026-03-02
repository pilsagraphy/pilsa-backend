package com.back.student.free.dto;

import lombok.Getter;
import lombok.Setter;

// 카테고리 목록 보내라고 되어있어서 Id하고 카테고리 이름 둘 다 보냄
@Getter
@Setter
public class CategoryResponse {
    private Long categoryId;
    private String name;
}