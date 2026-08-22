package com.back.admin.dashboard.mapper;

import com.back.admin.dashboard.dto.RecentReportResponse;
import com.back.admin.dashboard.dto.RecentUserResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    // 최근 N일(policy_settings.dashboard_new_user_period_days) 가입 + 영구차단/탈퇴 제외
    int countNewUsers();

    // 최근 N일(policy_settings.dashboard_new_post_period_days) 작성 게시글 (삭제 제외)
    int countNewPosts();

    // 처리 대기(status='pending') 신고 건수 (게시글+댓글 통합)
    int countPendingReports();

    // 전체 회원 수 (영구차단/탈퇴 제외)
    long countTotalUsers();

    // 최근 신고 N건 (게시글/댓글 통합, 최신순)
    List<RecentReportResponse> findRecentReports(@Param("limit") int limit);

    // 최근 가입 회원 N명
    List<RecentUserResponse> findRecentUsers(@Param("limit") int limit);
}
