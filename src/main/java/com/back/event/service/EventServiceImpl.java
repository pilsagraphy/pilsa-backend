package com.back.event.service;

import com.back.event.dto.*;
import com.back.event.exception.EventException;
import com.back.event.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth; // 날짜 계산을 위해 필요
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventMapper eventMapper;

    @Override
    public EventPageResponse getEventsByPeriod(String from, String to) {
        // 2026-03로 와도 날짜를 특정해서 와도 처리 가능하도록
        // 7자리(2026-03)로 오면 10자리(2026-03-01 / 2026-03-31)로 변환
        String formattedFrom = formatToStartDate(from);
        String formattedTo = formatToEndDate(to);

        log.info("조회 기간 변환: {} ~ {} -> {} ~ {}", from, to, formattedFrom, formattedTo);

        List<EventDataResponse> events = eventMapper.findEventsByPeriod(formattedFrom, formattedTo);
        return new EventPageResponse("일정 목록을 성공적으로 불러왔습니다.", events);
    }

    /**
     * 구글 캘린더 "URL로 추가" 구독용 iCalendar(ICS) 피드.
     *
     * 프론트의 [구독하기] 버튼은 이 피드 주소를
     * https://calendar.google.com/calendar/render?cid={URL인코딩한 피드 주소}
     * 로 여는 것이 전부다. 구글이 주기적으로(수 시간~하루) 이 URL을 다시 읽어가므로
     * 이후 등록/수정/삭제되는 일정이 구독자 캘린더에 자동 반영된다.
     * 구글 서버가 인증 없이 가져가야 하므로 이 피드는 PUBLIC 이다.
     */
    @Override
    public String buildCalendarFeed() {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//pilsagraphy//homepage//KO");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:필사그래피 일정");
        line(sb, "X-WR-TIMEZONE:Asia/Seoul");

        for (EventCalendarRow row : eventMapper.findAllForCalendar()) {
            line(sb, "BEGIN:VEVENT");
            // UID 는 일정마다 고정 — 같은 UID 로 다시 내려가면 구글이 "수정"으로 인식한다
            line(sb, "UID:pilsa-event-" + row.getEventId() + "@pilsagraphy");
            line(sb, "DTSTAMP:" + row.getDtstamp());
            // 시각 없는 종일 일정. DTEND 는 배타적이라 SQL 에서 종료일 + 1일로 만들어 온다
            line(sb, "DTSTART;VALUE=DATE:" + row.getStartDate());
            line(sb, "DTEND;VALUE=DATE:" + row.getEndDateExclusive());
            line(sb, "SUMMARY:" + escapeIcs(row.getTitle()));
            if (row.getCategory() != null && !row.getCategory().isBlank()) {
                line(sb, "CATEGORIES:" + escapeIcs(row.getCategory()));
            }
            if (row.getDescription() != null && !row.getDescription().isBlank()) {
                line(sb, "DESCRIPTION:" + escapeIcs(row.getDescription()));
            }
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    // RFC 5545 이스케이프: 백슬래시·쉼표·세미콜론·개행
    private String escapeIcs(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    // 한 줄 기록 + 75옥텟 초과 시 접기(folding). UTF-8 문자 중간이 잘리지 않게 문자 단위로 누적한다
    private void line(StringBuilder sb, String content) {
        int octets = 0;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (octets + size > 70) { // 이어지는 줄은 공백 1칸으로 시작 (RFC 5545 §3.1)
                sb.append("\r\n ");
                octets = 1;
            }
            sb.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        sb.append("\r\n");
    }

    // 시작일 변환: 2026-03 -> 2026-03-01
    private String formatToStartDate(String date) {
        if (date.length() == 7) return date + "-01";
        return date;
    }

    // 종료일 변환: 2026-03 -> 2026-03-31 (해당 월의 실제 마지막 날 계산)
    private String formatToEndDate(String date) {
        if (date.length() == 7) {
            YearMonth yearMonth = YearMonth.parse(date);
            return date + "-" + yearMonth.lengthOfMonth(); // 28, 30, 31일을 정확히 계산
        }
        return date;
    }
    }