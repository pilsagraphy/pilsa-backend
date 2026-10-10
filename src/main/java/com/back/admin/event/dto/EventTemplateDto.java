package com.back.admin.event.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 일정 템플릿 한 건 (event_templates). 요청·응답 공용 — 등록/수정 요청은 name·title·category·description·startTime·endTime 만 읽는다.
 * startTime/endTime 은 'HH:mm' 문자열, 비면 종일.
 */
@Data
public class EventTemplateDto {
    private Long templateId;
    private String name;
    private String title;
    private String category;
    private String description;
    private String startTime;
    private String endTime;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
