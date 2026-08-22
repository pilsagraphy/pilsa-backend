package com.back.admin.dashboard.controller;

import com.back.admin.dashboard.dto.AdminDashboardResponse;
import com.back.admin.dashboard.dto.RecentMemberResponse;
import com.back.admin.dashboard.dto.RecentReportResponse;
import com.back.admin.dashboard.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-대시보드",
        description = "관리자 홈 화면. api_endpoints 정본대로 통계 수치 / 최근 신고 / 최근 가입 회원 3개로 분리되어 있다. " +
                "일정 달력·월별 일정은 별도 엔드포인트 없이 회원 화면과 동일한 GET /api/event 를 그대로 사용한다.")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "관리자 대시보드 통계 수치", description = """
            관리자 홈의 숫자 카드 4개.

            ### 응답 예시
            ```json
            { "newMembers": 3, "pendingReports": 5, "newPosts": 12, "totalMembers": 80 }
            ```
            - newMembers / newPosts: 집계 기간은 `policy_settings`
              (`dashboard_new_user_period_days` / `dashboard_new_post_period_days`, 기본 1일)에서 로드한다.
              기준은 **자정**이다 — 1이면 오늘 00:00 부터, 7이면 오늘 포함 7일. (화면 문구가 '오늘'이라 당일 자정 기준)
            - pendingReports: 상태가 `pending`인 신고를 **대상 단위**로 센다(게시글+댓글 통합).
              신고 처리가 대상 단위로 pending 을 일괄 종료하므로, 행 단위로 세면 신고 관리 화면과 숫자가 갈린다.
            - newMembers / totalMembers: **영구차단·탈퇴 상태가 아닌 회원만** 센다.
            - 일정 달력은 기존 `GET /api/event?from=&to=` 를 그대로 쓴다(관리자도 로그인 회원이라 별도 엔드포인트 불필요).""")
    @GetMapping("/api/admin/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminDashboardService.getDashboard());
    }

    @Operation(summary = "관리자 대시보드 최근 신고 목록", description = """
            관리자 홈의 "최근 신고" 5건. 게시글 신고와 댓글 신고를 통합해 최신순으로 준다.

            ### 응답 예시
            ```json
            [
              { "targetType": "post", "targetId": 171, "postId": 171, "boardId": 2,
                "boardName": "자유게시판", "preview": "본문 앞부분", "createdAt": "2026-08-14T10:00:00" }
            ]
            ```
            - **대상 단위**로 묶여 있다 — 같은 글이 여러 번 신고돼도 1줄이며, `createdAt` 은 그 대상의 가장 최근 신고 시각.
            - preview: 신고 대상 자체의 본문 앞 30자(신고 관리 화면과 동일 규칙).
            - postId: 댓글 신고는 `targetId` 가 comment_id 라 그것만으로는 원글로 갈 수 없어 함께 준다.
              게시글 신고는 targetId 와 같은 값.
            - size: 표시 건수(선택, 기본 5). 1~100 으로 보정된다.""")
    @GetMapping("/api/admin/dashboard/recent-reports")
    public ResponseEntity<List<RecentReportResponse>> getRecentReports(
            @Parameter(description = "표시 건수 (기본 5, 1~100)")
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(adminDashboardService.getRecentReports(size));
    }

    @Operation(summary = "관리자 대시보드 최근 가입 회원 목록", description = """
            관리자 홈의 "최근 가입 회원" 5명.

            ### 응답 예시
            ```json
            [
              { "userId": 1, "memberType": "STUDENT", "loginId": "hong", "name": "홍길동",
                "joinedAt": "2026-08-14T09:00:00" }
            ]
            ```
            - **영구차단·탈퇴 회원은 제외**한다 — 같은 화면의 newMembers / totalMembers 와 기준을 맞춘다.
            - joinedAt: users.created_at. 마이페이지 응답(`GET /api/user/mypage`)과 필드명을 통일했다.
            - size: 표시 건수(선택, 기본 5). 1~100 으로 보정된다.""")
    @GetMapping("/api/admin/dashboard/recent-members")
    public ResponseEntity<List<RecentMemberResponse>> getRecentMembers(
            @Parameter(description = "표시 건수 (기본 5, 1~100)")
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(adminDashboardService.getRecentMembers(size));
    }
}
