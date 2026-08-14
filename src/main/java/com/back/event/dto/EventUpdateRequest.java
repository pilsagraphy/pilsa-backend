package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

// 수정 요청
@Getter
@Setter
public class EventUpdateRequest {
    private String title;
    private String category;
    private String description;
    private String startDate;
    private String endDate;
}
