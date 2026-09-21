package com.back.event.service;

import com.back.event.dto.EventCalendarRow;
import com.back.event.dto.EventImageRow;
import com.back.event.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 일정 이미지의 **바깥에서 열리는 절대 주소**를 만든다 — ICS 구독 피드와 구글 캘린더 동기화가 쓴다.
 *
 * 홈페이지 안에서는 상대 경로(/api/event/images/{id})면 되지만, 구글 캘린더·아이폰 캘린더는 우리 도메인을
 * 모르므로 https://pilsa.co.kr 을 앞에 붙여야 한다. 도메인은 app.public-url 로 바꿀 수 있고 기본은 운영 도메인이다
 * (nginx 가 pilsa.co.kr/api/ 를 백엔드로 넘긴다). PM: 일정 사진이 다른 캘린더에도 보였으면 좋겠다 (2026-09-21)
 */
@Component
@RequiredArgsConstructor
public class EventImageLinks {

    private final EventMapper eventMapper;

    @Value("${app.public-url:https://pilsa.co.kr}")
    private String publicUrl;

    public String toPublicUrl(Long imageId) {
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + "/api/event/images/" + imageId;
    }

    /** 일정 하나의 이미지 절대 주소들 (올린 순). 없으면 빈 목록 */
    public List<String> forEvent(Long eventId) {
        return eventMapper.findImagesByEventIds(List.of(eventId)).stream()
                .map(row -> toPublicUrl(row.getImageId()))
                .toList();
    }

    /** 여러 일정의 이미지를 한 번에 채운다 (구독 피드 전체·초기 동기화) */
    public void fill(List<EventCalendarRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(EventCalendarRow::getEventId).toList();
        Map<Long, List<String>> byEvent = eventMapper.findImagesByEventIds(ids).stream()
                .collect(Collectors.groupingBy(EventImageRow::getEventId,
                        Collectors.mapping(row -> toPublicUrl(row.getImageId()), Collectors.toList())));
        rows.forEach(row -> row.setImageUrls(byEvent.getOrDefault(row.getEventId(), List.of())));
    }

    /** 한 건 채우기 — null 이면 그대로 돌려준다 */
    public EventCalendarRow fill(EventCalendarRow row) {
        if (row != null) {
            row.setImageUrls(forEvent(row.getEventId()));
        }
        return row;
    }
}
