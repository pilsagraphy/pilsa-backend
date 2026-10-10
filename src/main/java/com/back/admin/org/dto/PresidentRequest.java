package com.back.admin.org.dto;

import lombok.Data;

/** 기수(역대 회장) 등록·수정 본문. endYear 가 null 이면 '현재' */
@Data
public class PresidentRequest {
    private Integer seqNo;
    private String name;
    private Integer startYear;
    private Integer endYear;
}
