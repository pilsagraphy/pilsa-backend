package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * iCalendar(ICS) 피드 생성용 일정 한 건.
 * 날짜는 종일 일정(VALUE=DATE) 규격에 맞춰 SQL 에서 미리 포맷해서 가져온다.
 *  - startDate : yyyyMMdd
 *  - endDateExclusive : 종료일 + 1일 (RFC 5545 의 DTEND 는 배타적)
 *  - dtstamp : 마지막 수정 시각 UTC (yyyyMMdd'T'HHmmss'Z')
 */
@Getter
@Setter
public class EventCalendarRow {
    private Long eventId;
    private String title;
    private String category;
    private String description;
    private String startDate;
    private String endDateExclusive;
    private String dtstamp;
}
