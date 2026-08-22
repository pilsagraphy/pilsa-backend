package com.back.admin.dashboard.controller;

import com.back.admin.dashboard.dto.AdminDashboardResponse;
import com.back.admin.dashboard.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-대시보드",
        description = "관리자 홈 화면 — 활동 요약(신규 가입자/처리 대기 신고/신규 게시글/전체 회원 수) + 최근 신고·최근 가입 회원 목록. " +
                "신규 가입자·신규 게시글의 집계 기간은 policy_settings 로 설정한다. " +
                "일정 달력·월별 일정은 별도 엔드포인트 없이 회원 화면과 동일한 GET /api/event 를 그대로 사용한다.")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "관리자 대시보드 요약", description = """
            관리자 홈 진입 시 호출한다.

            ### 응답 예시
            ```json
            {
              "newUserCount": 3,
              "pendingReportCount": 2,
              "newPostCount": 6,
              "totalUserCount": 121,
              "recentReports": [
                { "targetType": "post", "boardName": "자유게시판", "title": "글 제목", "reportedAt": "2026-05-03T10:12:00" }
              ],
              "recentUsers": [
                { "userId": 1, "loginId": "ch400", "name": "김본명", "memberType": "STUDENT", "createdAt": "2026-06-05T09:00:00" }
              ]
            }
            ```
            - newUserCount(신규 가입자) / newPostCount(신규 게시글): 집계 기간은 `policy_settings`
              (`dashboard_new_user_period_days` / `dashboard_new_post_period_days`, 기본 1일=오늘)에서 로드한다.
              newUserCount는 **영구차단·탈퇴 상태가 아닌 회원만** 센다.
            - pendingReportCount: 상태가 `pending`인 신고 건수(게시글+댓글 통합).
            - totalUserCount: **영구차단·탈퇴 상태가 아닌** 전체 회원 수.
            - recentReports.title: 신고 대상 게시글의 제목(댓글 신고는 소속 게시글의 제목).
            - 일정 달력·월별 일정은 기존 `GET /api/event?from=&to=` 를 그대로 사용한다(관리자도 로그인 회원이라 별도 엔드포인트 불필요).""")
    @GetMapping("/api/admin/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminDashboardService.getDashboard());
    }
}
