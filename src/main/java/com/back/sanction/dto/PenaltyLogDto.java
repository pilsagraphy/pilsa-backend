package com.back.sanction.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PenaltyLogDto {
    private Long penaltyId;
    private Long userId;
    private Integer points;
    private String targetType;
    private Long targetId;
    private Long sourceActionId;
    private LocalDateTime expiresAt;
}
