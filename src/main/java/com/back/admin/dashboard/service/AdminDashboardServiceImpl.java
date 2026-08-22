package com.back.admin.dashboard.service;

import com.back.admin.dashboard.dto.AdminDashboardResponse;
import com.back.admin.dashboard.dto.RecentReportResponse;
import com.back.admin.dashboard.dto.RecentUserResponse;
import com.back.admin.dashboard.mapper.AdminDashboardMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int RECENT_LIST_SIZE = 5;

    private final AdminDashboardMapper adminDashboardMapper;

    @Override
    public AdminDashboardResponse getDashboard() {
        AuthUtils.requireAdmin();

        int newUserCount = adminDashboardMapper.countNewUsers();
        int pendingReportCount = adminDashboardMapper.countPendingReports();
        int newPostCount = adminDashboardMapper.countNewPosts();
        long totalUserCount = adminDashboardMapper.countTotalUsers();
        List<RecentReportResponse> recentReports = adminDashboardMapper.findRecentReports(RECENT_LIST_SIZE);
        List<RecentUserResponse> recentUsers = adminDashboardMapper.findRecentUsers(RECENT_LIST_SIZE);

        return new AdminDashboardResponse(newUserCount, pendingReportCount, newPostCount, totalUserCount,
                recentReports, recentUsers);
    }
}
