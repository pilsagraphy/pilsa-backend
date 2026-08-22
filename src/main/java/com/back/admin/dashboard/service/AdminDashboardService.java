package com.back.admin.dashboard.service;

import com.back.admin.dashboard.dto.AdminDashboardResponse;
import com.back.admin.dashboard.dto.RecentMemberResponse;
import com.back.admin.dashboard.dto.RecentReportResponse;

import java.util.List;

public interface AdminDashboardService {

    AdminDashboardResponse getDashboard();

    List<RecentReportResponse> getRecentReports(int size);

    List<RecentMemberResponse> getRecentMembers(int size);
}
