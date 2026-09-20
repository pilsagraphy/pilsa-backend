package com.back.admin.dashboard.dto;

import com.back.admin.sanction.dto.BanPolicyDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 관리자 홈의 '운영 정책' 요약.
 * 정책은 policy_settings 와 ban_policy 에 있고 코드가 그 값을 읽어 쓴다 — 화면에도 같은 출처를 보여 줘야
 * 문서와 실제가 어긋나지 않는다. 신고·제재에 관련된 항목만 골라 준다.
 */
@Getter
@Setter
public class AdminPolicySummaryResponse {
    /** code → setting_value (policy_settings 에서 고른 항목만) */
    private Map<String, String> settings;
    /** 경고 횟수별 정지 규칙 (warning_no 오름차순) */
    private List<BanPolicyDto> banPolicies;
}
