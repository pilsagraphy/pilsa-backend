package com.back.student.free.dto;

import lombok.Getter;
import lombok.Setter;

// 5개 조회
@Getter
@Setter
public class FreeTop5Response {
    private Long postId;
    private String title;
}