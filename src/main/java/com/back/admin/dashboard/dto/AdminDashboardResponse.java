package com.back.admin.dashboard.dto;

import java.util.List;
import lombok.Getter;

// 관리자 홈 대시보드 응답 (오늘의 활동 요약 + 최근 신고/최근 가입 회원)
@Getter
public class AdminDashboardResponse {
    private final int newUserCount;            // 오늘 신규 가입자 수
    private final int pendingReportCount;       // 처리 대기 신고 대상 수 (게시글+댓글, 대상 단위)
    private final int newPostCount;             // 오늘 신규 작성 게시글 수
    private final long totalUserCount;          // 전체 회원 수
    private final List<RecentReportResponse> recentReports;
    private final List<RecentUserResponse> recentUsers;

    public AdminDashboardResponse(int newUserCount, int pendingReportCount, int newPostCount, long totalUserCount,
                                   List<RecentReportResponse> recentReports, List<RecentUserResponse> recentUsers) {
        this.newUserCount = newUserCount;
        this.pendingReportCount = pendingReportCount;
        this.newPostCount = newPostCount;
        this.totalUserCount = totalUserCount;
        this.recentReports = recentReports;
        this.recentUsers = recentUsers;
    }
}
