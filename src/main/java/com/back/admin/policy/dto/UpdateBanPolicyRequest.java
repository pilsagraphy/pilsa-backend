package com.back.admin.policy.dto;

import lombok.Getter;
import lombok.Setter;

/** PUT /api/admin/policies/ban/{warningNo} 본문 */
@Getter
@Setter
public class UpdateBanPolicyRequest {
    private String banType;     // temporary / permanent
    private Integer banDays;    // temporary 면 필수(1 이상), permanent 면 무시(null 저장)
    private String description; // 선택
}
