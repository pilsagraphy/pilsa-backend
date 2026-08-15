package com.back.event.service;

import com.back.event.dto.*;
import com.back.event.exception.EventException;
import com.back.event.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import com.back.global.security.AuthUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth; // 날짜 계산을 위해 필요
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventMapper eventMapper;

    // 관리자 권한 확인 (공통 유틸 사용)
    private void checkAdminRole() {
        if (!AuthUtils.isAdmin()) {
            throw new EventException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    // 카테고리 유효성 검증 — event_categories 에 등록된 값만 허용 (자유 입력 폐지, 2026-08-16)
    private void validateCategory(String category) {
        if (category != null && !category.isBlank() && !eventMapper.existsEventCategory(category)) {
            throw new EventException("유효하지 않은 일정 카테고리입니다. (GET /api/event/categories 참고)", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional
    public EventResponse createEvent(EventRequest request) {
        checkAdminRole();

        // 날짜 선후 관계 검증 로직 추가
        validateExecutionDates(request.getStartDate(), request.getEndDate());
        validateCategory(request.getCategory());

        // 등록 시에는 ERD 구조상 누가 등록했는지(user_id)가 필요하므로 가져옴
        Long userId = AuthUtils.currentUserId();

        eventMapper.insertEvent(request, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", request.getEventId());
        data.put("title", request.getTitle());

        return new EventResponse("새로운 일정이 등록되었습니다.", data);
    }

    private void validateExecutionDates(String startDate, String endDate) {
        if (startDate == null || endDate == null) {
            throw new EventException("시작일과 종료일은 필수 입력 항목입니다.", HttpStatus.BAD_REQUEST);
        }

        // 문자열을 비교 (YYYY-MM-DD 형식은 문자열 비교만으로도 선후 관계 확인 가능함)
        if (startDate.compareTo(endDate) > 0) {
            throw new EventException("시작일이 종료일보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional
    public EventResponse updateEvent(Long eventId, EventUpdateRequest request) {
        checkAdminRole(); // 권한만 확인
        validateCategory(request.getCategory());

        int updated = eventMapper.updateEvent(eventId, request);
        if (updated == 0) {
            throw new EventException("해당 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("updatedAt", LocalDateTime.now());

        return new EventResponse("일정 정보가 성공적으로 수정되었습니다.", data);
    }

    @Override
    @Transactional
    public EventResponse deleteEvent(Long eventId) {
        checkAdminRole(); // 권한만 확인

        int deleted = eventMapper.deleteEvent(eventId);
        if (deleted == 0) {
            throw new EventException("삭제할 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        return new EventResponse("일정이 정상적으로 삭제되었습니다.", null);
    }

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

    @Override
    public List<EventCategoryResponse> getEventCategories() {
        return eventMapper.findActiveEventCategories();
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