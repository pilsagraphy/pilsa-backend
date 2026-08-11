package com.back.sanction.dto;

import lombok.Data;

@Data
public class ModerationLogDto {
    private Long actionId;
    private String targetType;
    private Long targetId;
    private String appliedState;
    private Long reasonId;
    private String detail;
    private Long actedBy;
}
