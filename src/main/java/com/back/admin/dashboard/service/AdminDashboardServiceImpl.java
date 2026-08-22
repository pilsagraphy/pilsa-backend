package com.back.admin.dashboard.service;

import com.back.admin.dashboard.dto.AdminDashboardResponse;
import com.back.admin.dashboard.dto.RecentMemberResponse;
import com.back.admin.dashboard.dto.RecentReportResponse;
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
    private static final String NEW_MEMBER_PERIOD_CODE = "dashboard_new_user_period_days";
    private static final String NEW_POST_PERIOD_CODE = "dashboard_new_post_period_days";

    private final AdminDashboardMapper adminDashboardMapper;

    @Override
    public AdminDashboardResponse getDashboard() {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선

        return new AdminDashboardResponse(
                adminDashboardMapper.countNewMembers(NEW_MEMBER_PERIOD_CODE),
                adminDashboardMapper.countPendingReports(),
                adminDashboardMapper.countNewPosts(NEW_POST_PERIOD_CODE),
                adminDashboardMapper.countTotalMembers());
    }

    @Override
    public List<RecentReportResponse> getRecentReports() {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        return adminDashboardMapper.findRecentReports(RECENT_LIST_SIZE);
    }

    @Override
    public List<RecentMemberResponse> getRecentMembers() {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        return adminDashboardMapper.findRecentMembers(RECENT_LIST_SIZE);
    }
}
