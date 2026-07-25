package com.back.schedule.dto;

import lombok.Getter;
import lombok.Setter;

// 수정 요청
@Getter
@Setter
public class ScheduleUpdateRequest {
    private String title;
    private String startDate;
    private String endDate;
}
