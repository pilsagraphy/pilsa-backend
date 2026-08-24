package com.back.admin.dashboard.mapper;

import com.back.admin.dashboard.dto.RecentMemberResponse;
import com.back.admin.dashboard.dto.RecentReportResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    // 최근 N일 가입 (N = policy_settings 의 policyCode 값, 자정 기준) + 영구차단/탈퇴 제외
    int countNewMembers(@Param("policyCode") String policyCode);

    // 최근 N일 작성 게시글 (삭제 제외, 자정 기준)
    int countNewPosts(@Param("policyCode") String policyCode);

    // 처리 대기(status='pending') 신고 수 — 대상 단위 (게시글+댓글 통합)
    int countPendingReports();

    // 전체 회원 수 (영구차단/탈퇴 제외)
    long countTotalMembers();

    // 최근 신고 N건 (게시글/댓글 통합, 대상 단위 최신순)
    List<RecentReportResponse> findRecentReports(@Param("limit") int limit);

    // 최근 가입 회원 N명 (영구차단/탈퇴 제외)
    List<RecentMemberResponse> findRecentMembers(@Param("limit") int limit);
}
