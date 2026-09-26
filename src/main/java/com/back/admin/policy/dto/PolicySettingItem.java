package com.back.admin.policy.dto;

import lombok.Getter;
import lombok.Setter;

/** policy_settings 한 줄 (관리자 정책 설정 화면) */
@Getter
@Setter
public class PolicySettingItem {
    private Long settingId;
    private String code;
    private String settingValue;
    private String description;
}
