package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

// 일정 카테고리 한 건 (event_categories 테이블 — events.category 값의 정본)
@Getter
@Setter
public class EventCategoryResponse {
    private Long eventCategoryId;
    private String name;
}
