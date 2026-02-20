package com.back.aboutPilsa.hall_of_fame.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class HonorResponse {
    private Long donationId;
    private Long amount;
    private String displayName; // 익명일 경우 '익명후원자'로 변환됨
    private String affiliation;
    private String major;
    private String message;
    private LocalDateTime donatedAt;
    private boolean isAnonymous;
}