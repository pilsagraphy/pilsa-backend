package com.back.admin.dashboard.service;

import com.back.admin.dashboard.dto.AdminDashboardResponse;
import com.back.admin.dashboard.dto.AdminPolicySummaryResponse;
import com.back.admin.dashboard.dto.PolicySettingRow;
import com.back.admin.dashboard.dto.RecentMemberResponse;
import com.back.admin.dashboard.dto.RecentReportResponse;
import com.back.admin.common.AdminServiceSupport;
import com.back.admin.dashboard.mapper.AdminDashboardMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

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
    public List<RecentReportResponse> getRecentReports(int size) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        return adminDashboardMapper.findRecentReports(AdminServiceSupport.clampSize(size));
    }

    @Override
    public List<RecentMemberResponse> getRecentMembers(int size) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        return adminDashboardMapper.findRecentMembers(AdminServiceSupport.clampSize(size));
    }

    // 관리자 홈에 보여 줄 정책 코드. 신고·제재와 탈퇴 관련만 — 나머지(통계·업로드 등)는 운영진이 볼 일이 없다
    private static final List<String> SUMMARY_CODES = List.of(
            "auto_blind_threshold", "caution_per_delete", "cautions_per_warning",
            "caution_ttl_days", "warning_ttl_days", "rejoin_cooldown_days", "withdrawn_purge_days");

    @Override
    public AdminPolicySummaryResponse getPolicySummary() {
        AuthUtils.requireAdmin();
        Map<String, String> settings = new LinkedHashMap<>();
        for (PolicySettingRow row : adminDashboardMapper.findPolicySettings(SUMMARY_CODES)) {
            settings.put(row.getCode(), row.getSettingValue());
        }
        AdminPolicySummaryResponse response = new AdminPolicySummaryResponse();
        response.setSettings(settings);
        response.setBanPolicies(adminDashboardMapper.findBanPolicies());
        return response;
    }
}
