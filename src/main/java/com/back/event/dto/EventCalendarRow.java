package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * iCalendar(ICS) 피드·구글 캘린더 동기화용 일정 한 건.
 * 날짜·시각은 규격에 맞춰 SQL 에서 미리 포맷해서 가져온다.
 *  - startDate : yyyyMMdd (종일 일정용)
 *  - endDateExclusive : 종료일 + 1일 (RFC 5545 의 DTEND 는 배타적)
 *  - allDay : 시작·종료의 시각이 모두 00:00:00 인가. 시각을 지정한 일정은 종료가 00:00 일 수 없다
 *  - startDateTime / endDateTime : yyyyMMdd'T'HHmmss (한국 시각, 구글에 timeZone 과 함께 보낸다)
 *  - startUtc / endUtc : 같은 시각의 UTC 표기 (ICS 는 이걸 써야 어떤 캘린더 앱에서도 어긋나지 않는다)
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
    private Boolean allDay;
    private String startDateTime;
    private String endDateTime;
    private String startUtc;
    private String endUtc;
    private String dtstamp;

    // 일정 이미지의 절대 주소들 (EventImageLinks 가 채운다). ICS 의 ATTACH·설명, 구글 설명에 싣는다
    private java.util.List<String> imageUrls = new java.util.ArrayList<>();
}
