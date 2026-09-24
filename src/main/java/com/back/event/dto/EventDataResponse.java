package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

// 조회 응답용 - 실제 일정 목록
@Getter
@Setter
public class EventDataResponse {
    private Long eventId;
    private String title;
    private String category;
    private String description;
    private String startDate;
    private String endDate;

    // 'HH:mm'. 종일 일정이면 null — 화면은 이 값이 있을 때만 시각을 덧붙인다
    private String startTime;
    private String endTime;

    // 일정에 붙은 이미지(첨부). 서비스가 따로 조회해 채운다 — 없으면 빈 목록
    private java.util.List<EventImageResponse> images = new java.util.ArrayList<>();
}
