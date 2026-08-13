package com.back.event.dto;

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
public class EventPageResponse {
    private String message;
    private List<EventDataResponse> data; // 실제 일정 목록
}
