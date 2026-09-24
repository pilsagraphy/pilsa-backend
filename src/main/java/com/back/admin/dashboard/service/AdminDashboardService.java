package com.back.admin.dashboard.service;

import com.back.admin.dashboard.dto.AdminDashboardResponse;
import com.back.admin.dashboard.dto.AdminPolicySummaryResponse;
import com.back.admin.dashboard.dto.RecentMemberResponse;
import com.back.admin.dashboard.dto.RecentReportResponse;

import java.util.List;

public interface AdminDashboardService {

    AdminDashboardResponse getDashboard();

    List<RecentReportResponse> getRecentReports(int size);

    List<RecentMemberResponse> getRecentMembers(int size);

    /** 관리자 홈 '운영 정책' 요약 (신고·제재 관련 policy_settings + ban_policy) */
    AdminPolicySummaryResponse getPolicySummary();
}
