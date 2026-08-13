package com.back.report.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportedContentResponse {
    private Long reportId;
    private String targetType;
    private Long targetId;
    private Long postId;
    private Long boardId;
    private String boardCode;
    private Long reasonId;
    private String reasonLabel;
    private String detail;
    private String status;
    private Integer activeFlag;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
