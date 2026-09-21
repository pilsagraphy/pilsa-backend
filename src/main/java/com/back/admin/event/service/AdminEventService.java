package com.back.admin.event.service;

import com.back.event.dto.EventImageResponse;
import com.back.event.dto.EventImageRow;
import com.back.event.dto.EventRequest;
import com.back.event.dto.EventResponse;
import com.back.event.dto.EventUpdateRequest;
import com.back.event.exception.EventException;
import com.back.admin.event.mapper.AdminEventMapper;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import com.back.event.service.EventServiceImpl;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;
import com.back.mypage.calendar.service.GoogleCalendarSyncService;
import com.back.mypage.notification.dto.NotificationType;
import com.back.mypage.notification.mapper.NotificationMapper;
import com.back.mypage.notification.service.NotificationPublisher;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 일정 관리(관리자) — 등록/수정/삭제.
 *
 * 회원 달력 조회·캘린더 구독 피드는 com.back.event 가 담당하고, 여기는 관리자 화면 전용이다.
 * 매퍼도 이 패키지에서 직접 관리한다({@link AdminEventMapper}) — 관리자 쿼리와 회원 조회 쿼리는 겹치지 않는다.
 *
 * 일정이 바뀌면 구글 캘린더 연동 사용자들에게도 반영한다({@link GoogleCalendarSyncService}).
 * 반영은 비동기이며 커밋 이후에 시작한다 — 관리자 화면이 구글 응답을 기다리지 않게 하고,
 * 구글이 느리거나 실패해도 일정 등록 자체는 성공으로 끝나게 하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEventService {

    private final AdminEventMapper adminEventMapper;
    private final GoogleCalendarSyncService calendarSyncService;
    private final FileStorageUtil fileStorageUtil;
    private final NotificationPublisher notificationPublisher;
    private final NotificationMapper notificationMapper;

    private static final String[] WEEKDAYS_KO = {"월", "화", "수", "목", "금", "토", "일"};

    /** "9월 30일 (목) ~ 11월 20일 (금)" · 하루면 "9월 30일 (목)" · 시각이 있으면 " 14:00 ~ 16:00" 을 덧붙인다 */
    static String formatPeriod(String startDate, String endDate, String startTime, String endTime) {
        String start = formatKoreanDate(startDate);
        String end = formatKoreanDate(endDate);
        String period = start.equals(end) ? start : start + " ~ " + end;
        if (startTime != null && !startTime.isBlank() && endTime != null && !endTime.isBlank()) {
            period += " " + startTime + " ~ " + endTime;
        }
        return period;
    }

    private static String formatKoreanDate(String yyyyMmDd) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(yyyyMmDd);
            return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 (" + WEEKDAYS_KO[d.getDayOfWeek().getValue() - 1] + ")";
        } catch (Exception e) {
            return yyyyMmDd;
        }
    }

    /** 새 일정 알림 — 회원 전원. 실패해도 등록은 되돌리지 않는다 (알림은 부가 기능) */
    private void notifyNewEvent(Long eventId, EventRequest request) {
        try {
            // 제목 "[구분] 일정 제목", 본문은 기간. 이모지는 푸시에서만 붙인다 (알림함은 아이콘)
            String category = request.getCategory() == null ? "" : "[" + request.getCategory() + "] ";
            String title = category + request.getTitle();
            String message = formatPeriod(request.getStartDate(), request.getEndDate(),
                    request.getStartTime(), request.getEndTime());
            String finalTitle = title.length() > 100 ? title.substring(0, 97) + "…" : title;
            for (Long receiverId : notificationMapper.findAllActiveUserIds()) {
                notificationPublisher.publish(receiverId, NotificationType.EVENT, "event", eventId, null,
                        finalTitle, message);
            }
        } catch (Exception e) {
            log.warn("일정 알림 발행 실패 - eventId: {}, {}", eventId, e.getMessage());
        }
    }

    /** 일정 하나에 붙일 수 있는 이미지 수 — 포스터·안내 사진 몇 장이면 충분하고, 더 많으면 게시판이 맞다 */
    private static final int MAX_IMAGES_PER_EVENT = 10;

    /**
     * 일정 이미지 업로드. 이미지 파일만 받고, 저장 경로는 uploads/events/{eventId}/ 다.
     * 올린 순서대로 sort_order 를 매긴다. 일정이 없거나 지워졌으면 404.
     */
    @Transactional
    public List<EventImageResponse> uploadImages(Long eventId, List<MultipartFile> files) {
        checkAdminRole();
        if (!adminEventMapper.existsEvent(eventId)) {
            throw new EventException("해당 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        if (files == null || files.isEmpty()) {
            throw new EventException("올릴 이미지를 골라 주세요.", HttpStatus.BAD_REQUEST);
        }
        int existing = adminEventMapper.countImages(eventId);
        if (existing + files.size() > MAX_IMAGES_PER_EVENT) {
            throw new EventException("이미지는 일정 하나에 " + MAX_IMAGES_PER_EVENT + "장까지 붙일 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        for (MultipartFile file : files) {
            String type = file.getContentType();
            if (file.isEmpty() || type == null || !type.startsWith("image/") || type.contains("svg")) {
                throw new EventException("이미지 파일(jpg · png · gif · webp)만 올릴 수 있습니다.", HttpStatus.BAD_REQUEST);
            }
        }

        List<EventImageResponse> saved = new ArrayList<>();
        int order = existing;
        for (MultipartFile file : files) {
            String url = fileStorageUtil.save(file, "uploads/events/" + eventId);
            EventImageRow row = new EventImageRow();
            row.setEventId(eventId);
            row.setFileUrl(url);
            row.setFileName(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
            row.setFileType(file.getContentType());
            row.setFileSize(file.getSize());
            adminEventMapper.insertImage(row, order++);
            saved.add(EventServiceImpl.toImageResponse(row));
        }
        return saved;
    }

    /** 일정 이미지 삭제 — 행은 소프트 삭제, 파일은 커밋 뒤에 지운다 (롤백되면 파일이 살아 있어야 한다) */
    @Transactional
    public void deleteImage(Long eventId, Long imageId) {
        checkAdminRole();
        EventImageRow row = adminEventMapper.findImage(eventId, imageId);
        if (row == null) {
            throw new EventException("해당 이미지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        adminEventMapper.softDeleteImage(imageId);
        fileStorageUtil.deleteAfterCommit(List.of(row.getFileUrl()));
    }

    // 관리자 권한 확인 (공통 유틸 사용)
    private void checkAdminRole() {
        if (!AuthUtils.isAdmin()) {
            throw new EventException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * 카테고리 정규화 — event_categories 에 등록된 이름만 허용하고, 저장은 항상 **정본 표기**로 한다.
     *
     * 존재 확인(boolean)이 아니라 이름을 되받아 쓰는 이유: 콜레이션이 대소문자를 무시해서(utf8mb4_0900_ai_ci)
     * "mt" 도 검증을 통과하는데, 그대로 저장하면 events.category 에 정본("MT")과 다른 표기가 섞인다.
     * 앞뒤 공백도 여기서 털어낸다 — 프론트가 " MT " 를 보내도 400 이 되지 않게.
     * 빈 값은 NULL 로 통일한다(카테고리 미지정). 빈 문자열이 그대로 저장되던 것을 막는다.
     */
    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String name = adminEventMapper.findActiveCategoryName(category.trim());
        if (name == null) {
            throw new EventException("유효하지 않은 일정 카테고리입니다. (GET /api/event/categories 참고)", HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    /**
     * 'HH:mm' 형식과 같은 날의 앞뒤 관계를 확인한다.
     *
     * 시각은 선택 사항이다 — 둘 다 비면 종일 일정이다. 한쪽만 오면 나머지를 00:00 으로 채우는 대신
     * 거절한다. 14:00 ~ 00:00 처럼 의도를 알 수 없는 값이 저장되는 것보다 낫다.
     */
    private void validateTimes(String startDate, String endDate, String startTime, String endTime) {
        boolean hasStart = startTime != null && !startTime.isBlank();
        boolean hasEnd = endTime != null && !endTime.isBlank();

        if (hasStart != hasEnd) {
            throw new EventException("시작 시각과 종료 시각은 함께 입력해야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (!hasStart) {
            return; // 종일 일정
        }
        if (!startTime.matches("\\d{2}:\\d{2}") || !endTime.matches("\\d{2}:\\d{2}")) {
            throw new EventException("시각 형식이 올바르지 않습니다. (HH:mm)", HttpStatus.BAD_REQUEST);
        }
        // 날짜가 다르면 19:00 ~ 09:00 도 정상이다. 같은 날일 때만 앞뒤를 따진다
        if (startDate != null && startDate.equals(endDate) && endTime.compareTo(startTime) < 0) {
            throw new EventException("종료 시각은 시작 시각보다 빠를 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public EventResponse createEvent(EventRequest request) {
        checkAdminRole();

        // 날짜 선후 관계 검증
        validateExecutionDates(request.getStartDate(), request.getEndDate());
        validateTimes(request.getStartDate(), request.getEndDate(),
                request.getStartTime(), request.getEndTime());
        request.setCategory(normalizeCategory(request.getCategory()));

        // 등록 시에는 ERD 구조상 누가 등록했는지(user_id)가 필요하므로 가져옴
        Long userId = AuthUtils.currentUserId();

        adminEventMapper.insertEvent(request, userId);

        Long eventId = request.getEventId();
        afterCommit(() -> calendarSyncService.onEventCreated(eventId));
        // 회원 전원에게 알림 (PM, 2026-09-21)
        notifyNewEvent(eventId, request);

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("title", request.getTitle());

        return new EventResponse("새로운 일정이 등록되었습니다.", data);
    }

    /**
     * 커밋이 끝난 뒤에 실행한다.
     *
     * 트랜잭션 안에서 바로 부르면 비동기 스레드가 아직 커밋되지 않은 행을 조회해
     * "일정을 찾지 못했다"로 조용히 넘어가 버린다. 트랜잭션이 없으면(테스트 등) 즉시 실행한다.
     */
    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
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

    @Transactional
    public EventResponse updateEvent(Long eventId, EventUpdateRequest request) {
        checkAdminRole(); // 권한만 확인

        // 존재 확인이 먼저다 — 없는 일정에 잘못된 카테고리를 보냈을 때 400 이 아니라 404 가 나가야 한다.
        // (UPDATE 결과로만 판정하면 카테고리 검증이 앞서 실행돼 순서가 뒤집힌다)
        if (!adminEventMapper.existsEvent(eventId)) {
            throw new EventException("해당 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        // 부분 수정이므로 미전송(null)이면 기존 값을 그대로 둔다 — 빈 문자열도 여기서 null 이 되어 손대지 않는다
        request.setCategory(normalizeCategory(request.getCategory()));

        int updated = adminEventMapper.updateEvent(eventId, request);
        if (updated == 0) {
            // 확인과 UPDATE 사이에 삭제된 경우
            throw new EventException("해당 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        afterCommit(() -> calendarSyncService.onEventUpdated(eventId));

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("updatedAt", LocalDateTime.now());

        return new EventResponse("일정 정보가 성공적으로 수정되었습니다.", data);
    }

    @Transactional
    public EventResponse deleteEvent(Long eventId) {
        checkAdminRole(); // 권한만 확인

        int deleted = adminEventMapper.deleteEvent(eventId);
        if (deleted == 0) {
            throw new EventException("삭제할 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        // 소프트 삭제라 events 행은 남지만, 사용자 캘린더에서는 지워야 한다.
        // 매핑 테이블의 google_event_id 만 있으면 되므로 일정 본문을 다시 읽지 않는다.
        afterCommit(() -> calendarSyncService.onEventDeleted(eventId));

        return new EventResponse("일정이 정상적으로 삭제되었습니다.", null);
    }
}
