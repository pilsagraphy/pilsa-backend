package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

// 조회 응답용 - 실제 일정 목록
@Getter
@Setter
public class EventDataResponse {
    private Long eventId;
    private String title;
    private String startDate;
    private String endDate;
}
