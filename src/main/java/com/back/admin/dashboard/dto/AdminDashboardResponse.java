package com.back.admin.dashboard.dto;

import lombok.Getter;

// 관리자 홈 대시보드 통계 수치 (GET /api/admin/dashboard).
// 최근 신고·최근 가입 회원 목록은 api_endpoints 정본대로 /recent-reports, /recent-members 로 분리되어 있다.
@Getter
public class AdminDashboardResponse {
    private final int newMembers;        // 신규 가입자 수 (집계 기간은 policy_settings)
    private final int pendingReports;    // 처리 대기 신고 수 (대상 단위)
    private final int newPosts;          // 신규 게시글 수 (집계 기간은 policy_settings)
    private final long totalMembers;     // 전체 회원 수 (영구차단·탈퇴 제외)

    public AdminDashboardResponse(int newMembers, int pendingReports, int newPosts, long totalMembers) {
        this.newMembers = newMembers;
        this.pendingReports = pendingReports;
        this.newPosts = newPosts;
        this.totalMembers = totalMembers;
    }
}
