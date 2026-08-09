package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

// 등록 요청
@Getter
@Setter
public class EventRequest {
    private String title;
    private String startDate;
    private String endDate;

    private Long eventId;
}