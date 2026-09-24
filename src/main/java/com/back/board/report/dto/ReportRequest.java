package com.back.board.report.dto;

import lombok.Data;

@Data
public class ReportRequest {
    private String targetType; // post / comment
    private Long targetId;
    private Long reasonId;
    private String detail;
}
