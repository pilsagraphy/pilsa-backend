package com.back.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

// 조회 응답용
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePageResponse {
    private String message;
    private List<ScheduleDataResponse> data; // 실제 일정 목록
}
