package com.back.admin.donation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 명예의 전당 관리 한 행 (donations). userId 는 후원한 회원(필수, 이름으로 찾아 고른다). photoUrl 은 /uploads/Honor/.. 공개 정적 경로 */
@Data
public class DonationAdminDto {
    private Long donationId;
    private Long userId;
    private String userName;
    private String userLoginId;
    private String displayName;
    private Long amount;
    private String affiliation;
    private String major;
    private String message;
    private LocalDateTime donatedAt;
    private Boolean isAnonymous;
    private String photoUrl;
    private String state;
}
