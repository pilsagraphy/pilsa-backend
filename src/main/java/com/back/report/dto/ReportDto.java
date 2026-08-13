package com.back.report.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportDto {
    private Long reportId;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private Long reasonId;
    private String detail;
    private String status;
    private LocalDateTime createdAt;
}
