package com.back.student.info.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InfoUpdateRequest {
    private String title;
    private String content;
    private Long categoryId;
}