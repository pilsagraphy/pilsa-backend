package com.back.admin.dashboard.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 대시보드 "최근 가입 회원" 한 행
@Getter
@Setter
public class RecentUserResponse {
    private Long userId;
    private String loginId;         // ID (예: ch400)
    private String name;
    private String memberType;      // STUDENT / ALUMNI
    private LocalDateTime createdAt;
}
