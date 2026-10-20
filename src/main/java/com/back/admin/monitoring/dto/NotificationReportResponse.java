package com.back.admin.monitoring.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 알림 발송·읽음 (PM 2026-10-20) — notifications 행 기준. 알림함 알림(normal·deleted)과 푸시 전용 알림(push_only, 주간 인기 글)을 함께 센다.
 * 읽음은 알림함에서 누르거나 푸시를 눌러 읽음 API 가 찍은 것 ('모두 읽음' 도 포함). 기기 도착 여부는 DB 에 없다
 */
@Data
public class NotificationReportResponse {
    private int days;
    private List<TypeRow> types = new ArrayList<>();
    private List<BatchRow> batches = new ArrayList<>();

    @Data
    public static class TypeRow {
        private String type;
        private int sent;
        private int read;
        /** read / sent (0~1), 발송 0 이면 null */
        private Double readRate;
        /** 읽은 알림의 발송→읽음 분 중앙값, 읽은 게 없으면 null */
        private Integer medianReadMinutes;
    }

    /** 날짜·종류별 발송 묶음 */
    @Data
    public static class BatchRow {
        private LocalDate date;
        private String type;
        /** 그 묶음의 대표 제목 (가장 최근 것) */
        private String title;
        private int sent;
        private int read;
    }

    /** 집계용 — 읽은 알림 한 건의 종류와 걸린 분 */
    @Data
    public static class ReadMinutesRow {
        private String type;
        private Integer minutes;
    }

    /** 묶음을 눌렀을 때의 수신자 */
    @Data
    public static class RecipientRow {
        private Long notificationId;
        private Long userId;
        private String name;
        private String loginId;
        private String title;
        /** normal | push_only | deleted */
        private String state;
        private boolean read;
        private LocalDateTime sentAt;
        private LocalDateTime readAt;
    }
}
