package com.back.admin.policy.controller;

import com.back.admin.policy.dto.AdminPolicyResponse;
import com.back.admin.policy.dto.BanPolicyItem;
import com.back.admin.policy.dto.PolicySettingItem;
import com.back.admin.policy.dto.UpdateBanPolicyRequest;
import com.back.admin.policy.dto.UpdateSettingRequest;
import com.back.admin.policy.service.AdminPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-운영 관리-정책 설정",
        description = "policy_settings(정책 수치 · 가입 형식 · 통계 기준 · 알림 스위치)와 ban_policy(경고→정지 단계표) 조회·수정. "
                + "조회는 관리자 누구나, 수정은 admin_level 3.")
public class AdminPolicyController {

    private final AdminPolicyService service;

    @Operation(summary = "정책 설정 전체", description = """
            policy_settings 전 행(code 순) + ban_policy 3단계. 알림 설정 화면은 같은 응답에서 `notify_` 로 시작하는 키만 쓴다.
            ```json
            { "settings": [ { "settingId": 1, "code": "caution_per_delete", "settingValue": "2", "description": "삭제 1건당 주의 포인트" } ],
              "banPolicies": [ { "banPolicyId": 1, "code": "BAN_W1", "warningNo": 1, "banType": "temporary", "banDays": 7, "description": "경고 1점: 1주일 정지" } ] }
            ```""")
    @GetMapping("/api/admin/policies")
    public ResponseEntity<AdminPolicyResponse> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @Operation(summary = "정책 값 수정 (admin_level 3)", description = """
            본문 `{ "settingValue": "3", "description": "선택" }`. 키 이름으로 형식을 검사한다 —
            `notify_*` 는 0/1, `*_regex` 는 컴파일 가능한 정규식, `*_extensions` 는 `jpg,png` 꼴, 나머지는 숫자.
            없는 키 404, 형식 오류 400, 레벨 부족 403. 바뀐 행을 돌려준다.""")
    @PutMapping("/api/admin/policies/settings/{code}")
    public ResponseEntity<PolicySettingItem> updateSetting(@PathVariable String code,
                                                           @RequestBody UpdateSettingRequest request) {
        return ResponseEntity.ok(service.updateSetting(code, request));
    }

    @Operation(summary = "정지 단계 수정 (admin_level 3)", description = """
            경로의 warningNo 는 1~3. 본문 `{ "banType": "temporary", "banDays": 7, "description": "선택" }`.
            permanent 면 banDays 는 무시되고 NULL 로 저장된다. 바뀐 행을 돌려준다.""")
    @PutMapping("/api/admin/policies/ban/{warningNo}")
    public ResponseEntity<BanPolicyItem> updateBanPolicy(@PathVariable int warningNo,
                                                         @RequestBody UpdateBanPolicyRequest request) {
        return ResponseEntity.ok(service.updateBanPolicy(warningNo, request));
    }
}
