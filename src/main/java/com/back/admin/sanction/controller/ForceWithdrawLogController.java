package com.back.admin.sanction.controller;

import com.back.admin.sanction.dto.ForceWithdrawLogResponse;
import com.back.admin.sanction.mapper.ForceWithdrawLogMapper;
import com.back.global.security.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 강제 탈퇴 이력 — 제재 회원 관리 화면 아래 표 (PM 2026-10-17 "강제탈퇴 이력도 제재 회원 관리에서").
 * 조회뿐이라 서비스 계층 없이 매퍼를 바로 쓴다. 기록은 WithdrawService.forceWithdraw 가 남긴다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-제재 회원 관리")
public class ForceWithdrawLogController {

    private static final int MAX_SIZE = 100;

    private final ForceWithdrawLogMapper mapper;

    @Operation(summary = "강제 탈퇴 이력",
            description = """
                    최신순. 대상은 가린 이름·아이디와 회원 번호만 (탈퇴 즉시 원문 파기 원칙). 보관 policy_settings.force_withdraw_log_days(기본 365일).

                    ### 요청 예시
                    ```
                    GET /api/admin/sanctions/withdrawals?page=1&size=20&keyword=학번
                    ```
                    keyword 는 가린 이름·가린 아이디·사유·회원 번호에서 찾는다.

                    ### 응답 예시
                    ```json
                    {"items":[{"logId":1,"userId":93,"maskedName":"네*트","maskedLoginId":"na***t","memberType":"STUDENT",
                      "reason":"동아리 회원 아님","withdrawnBy":4,"withdrawnByName":"박건희","createdAt":"2026-10-17T23:10:00"}],
                     "totalCount":1,"totalPages":1,"page":1,"size":20}
                    ```""")
    @GetMapping("/api/admin/sanctions/withdrawals")
    @Transactional(readOnly = true)
    public ResponseEntity<ForceWithdrawLogResponse> list(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         @RequestParam(required = false) String keyword) {
        AuthUtils.requireAdmin();
        int p = Math.max(1, page);
        int s = Math.max(1, Math.min(size, MAX_SIZE));
        String k = keyword == null || keyword.isBlank() ? null : keyword.trim();

        ForceWithdrawLogResponse res = new ForceWithdrawLogResponse();
        res.setItems(mapper.findPage(k, (p - 1) * s, s));
        res.setTotalCount(mapper.count(k));
        res.setTotalPages((int) Math.max(1, (res.getTotalCount() + s - 1) / s));
        res.setPage(p);
        res.setSize(s);
        return ResponseEntity.ok(res);
    }
}
