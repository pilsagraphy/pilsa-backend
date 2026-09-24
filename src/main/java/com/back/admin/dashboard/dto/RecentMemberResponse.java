package com.back.admin.dashboard.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 최근 가입 회원 한 행 (GET /api/admin/dashboard/recent-members). 영구차단·탈퇴 회원 제외.
@Getter
@Setter
public class RecentMemberResponse {
    private Long userId;
    private String memberType;      // STUDENT / ALUMNI
    private String loginId;
    private String name;
    private LocalDateTime joinedAt; // users.created_at — 마이페이지 응답과 필드명 통일
}
