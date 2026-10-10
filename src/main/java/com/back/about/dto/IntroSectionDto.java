package com.back.about.dto;

import lombok.Data;

/** 동아리 소개 문단 한 칸 (intro_sections) — 제목 + 본문(줄바꿈 유지). 운영 관리에서 고친다 (PM 2026-10-11) */
@Data
public class IntroSectionDto {
    private Long sectionId;
    private String title;
    private String content;
    private Integer sortOrder;
    private String state;
}
