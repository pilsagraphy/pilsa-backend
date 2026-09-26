package com.back.admin.policy.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 정책 설정 화면 전체 — policy_settings 전부 + ban_policy 단계표 */
@Getter
@Setter
public class AdminPolicyResponse {
    private List<PolicySettingItem> settings;
    private List<BanPolicyItem> banPolicies;
}
