package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

// 등록 요청
@Getter
@Setter
public class EventRequest {
    private String title;
    private String category;
    private String description;
    private String startDate;
    private String endDate;

    // 'HH:mm'. 비우면 종일 일정으로 저장한다 (start_at·end_at 의 시각이 00:00:00)
    private String startTime;
    private String endTime;

    // 알림 보내기 여부 — 관리자가 정한다. null 이면 등록은 보내고 수정은 안 보낸다
    private Boolean notify;

    private Long eventId;
}