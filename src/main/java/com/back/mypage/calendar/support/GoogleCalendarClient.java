package com.back.mypage.calendar.support;

import com.back.event.dto.EventCalendarRow;
import com.back.global.oauth.GoogleIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 구글 캘린더 events API 호출 (insert / update / delete).
 *
 * 동아리 일정은 전부 종일(all-day) 일정이라 start/end 를 dateTime 이 아니라 date 로 보낸다.
 * RFC 5545 와 마찬가지로 구글의 end.date 도 배타적이므로 종료일 + 1일을 넣어야 한다 —
 * 이 계산은 이미 EventCalendarRow.endDateExclusive 가 해두고 있어 그대로 쓴다.
 */
@Slf4j
@Component
public class GoogleCalendarClient {

    private static final String BASE_URL = "https://www.googleapis.com/calendar/v3/calendars";

    private final RestClient restClient = RestClient.create();

    /** @return 구글이 발급한 이벤트 ID */
    public String insertEvent(String accessToken, String calendarId, EventCalendarRow event) {
        try {
            JsonNode response = restClient.post()
                    .uri(BASE_URL + "/{calendarId}/events", calendarId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toGoogleEvent(event))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || response.path("id").asText(null) == null) {
                throw new GoogleIntegrationException("구글 캘린더가 이벤트 ID 를 반환하지 않았습니다.", HttpStatus.BAD_GATEWAY);
            }
            return response.path("id").asText();
        } catch (RestClientResponseException e) {
            throw translate(e, "일정 추가");
        }
    }

    public void updateEvent(String accessToken, String calendarId, String googleEventId, EventCalendarRow event) {
        try {
            restClient.put()
                    .uri(BASE_URL + "/{calendarId}/events/{eventId}", calendarId, googleEventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toGoogleEvent(event))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw translate(e, "일정 수정");
        }
    }

    /**
     * 사용자가 이미 구글 캘린더에서 직접 지웠으면 404/410 이 온다.
     * 우리 목적(그 일정이 사용자 캘린더에 없는 상태)은 달성됐으므로 성공으로 취급한다.
     */
    public void deleteEvent(String accessToken, String calendarId, String googleEventId) {
        try {
            restClient.delete()
                    .uri(BASE_URL + "/{calendarId}/events/{eventId}", calendarId, googleEventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.GONE) {
                log.debug("구글 캘린더에 이미 없는 이벤트 삭제 요청 - 성공으로 처리: {}", googleEventId);
                return;
            }
            throw translate(e, "일정 삭제");
        }
    }

    private Map<String, Object> toGoogleEvent(EventCalendarRow event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", event.getTitle());
        body.put("description", buildDescription(event));
        body.put("start", Map.of("date", toDashedDate(event.getStartDate())));
        body.put("end", Map.of("date", toDashedDate(event.getEndDateExclusive())));

        // 우리가 넣은 일정임을 사용자 캘린더에서 구분할 수 있게 표시해 둔다.
        // 나중에 "우리가 넣은 것만 정리" 할 때도 근거가 된다.
        body.put("source", Map.of("title", "필사그래피", "url", "https://pilsa.co.kr"));
        body.put("extendedProperties", Map.of(
                "private", Map.of("pilsaEventId", String.valueOf(event.getEventId()))
        ));
        return body;
    }

    private String buildDescription(EventCalendarRow event) {
        StringBuilder sb = new StringBuilder();
        if (event.getCategory() != null && !event.getCategory().isBlank()) {
            sb.append("[").append(event.getCategory()).append("]\n");
        }
        if (event.getDescription() != null) {
            sb.append(event.getDescription());
        }
        return sb.toString();
    }

    /** EventCalendarRow 는 ICS 규격에 맞춰 yyyyMMdd 로 오는데, 구글 API 는 yyyy-MM-dd 를 받는다. */
    private String toDashedDate(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.length() != 8) {
            return yyyyMMdd;
        }
        return yyyyMMdd.substring(0, 4) + "-" + yyyyMMdd.substring(4, 6) + "-" + yyyyMMdd.substring(6, 8);
    }

    private GoogleIntegrationException translate(RestClientResponseException e, String action) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        log.warn("구글 캘린더 {} 실패 - status={}, body={}", action, e.getStatusCode(), e.getResponseBodyAsString());

        // 401: access token 만료/무효 → 상위에서 refresh 후 재시도할 수 있게 그대로 올린다
        if (status == HttpStatus.UNAUTHORIZED) {
            return new GoogleIntegrationException("구글 인증이 만료되었습니다.", HttpStatus.UNAUTHORIZED);
        }
        // 403: 사용자가 구글 계정 설정에서 권한을 회수했거나 할당량 초과
        if (status == HttpStatus.FORBIDDEN) {
            return new GoogleIntegrationException("구글 캘린더 접근 권한이 없습니다. 재연동이 필요합니다.", HttpStatus.FORBIDDEN);
        }
        return new GoogleIntegrationException("구글 캘린더 " + action + "에 실패했습니다.", HttpStatus.BAD_GATEWAY);
    }
}
