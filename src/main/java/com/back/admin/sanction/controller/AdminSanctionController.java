package com.back.admin.sanction.controller;

import com.back.admin.sanction.dto.ReportedContentResponse;
import com.back.admin.sanction.dto.SanctionResponse;
import com.back.admin.sanction.dto.SanctionedUserDetailResponse;
import com.back.admin.sanction.dto.SanctionedUserResponse;
import com.back.admin.sanction.service.SanctionAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import com.back.global.security.AuthUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminSanctionController {

    private final SanctionAdminService sanctionAdminService;

    // 현재 제재(정지/영구차단) 중인 회원 목록
    @GetMapping("/api/admin/sanctions/users")
    public ResponseEntity<List<SanctionedUserResponse>> getSanctionedUsers() {
        log.info("제재 회원 목록 조회 요청");
        return ResponseEntity.ok(sanctionAdminService.getSanctionedUsers());
    }

    // 특정 회원의 현재 제재 현황
    @GetMapping("/api/admin/sanctions/users/{userId}")
    public ResponseEntity<SanctionedUserDetailResponse> getSanctionedUserDetail(@PathVariable Long userId) {
        log.info("제재 회원 현황 조회 요청 - userId: {}", userId);
        return ResponseEntity.ok(sanctionAdminService.getSanctionedUserDetail(userId));
    }

    // 특정 회원이 받은 신고 내역 전체 (제재회원 관리 화면3) - report 패키지에서 이동 (PM 피드백)
    @GetMapping("/api/admin/sanctions/users/{userId}/reports")
    public ResponseEntity<List<ReportedContentResponse>> getReportsByTargetAuthor(@PathVariable Long userId) {
        log.info("회원별 신고 내역 조회 요청 - userId: {}", userId);
        return ResponseEntity.ok(sanctionAdminService.getReportsByTargetAuthor(userId));
    }

    // 관리자 수동 제재 해제
    @PostMapping("/api/admin/sanctions/users/{userId}/lift")
    public ResponseEntity<SanctionResponse> liftBan(@PathVariable Long userId) {
        Long adminUserId = AuthUtils.currentUserId();
        log.info("제재 해제 요청 - 대상 userId: {}, 처리 관리자: {}", userId, adminUserId);
        sanctionAdminService.liftBan(userId, adminUserId);
        return ResponseEntity.ok(new SanctionResponse("제재가 해제되었습니다."));
    }
}
