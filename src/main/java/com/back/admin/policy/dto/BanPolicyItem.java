package com.back.admin.policy.dto;

import lombok.Getter;
import lombok.Setter;

/** ban_policy 한 줄 — 경고 n회 → 정지 단계 */
@Getter
@Setter
public class BanPolicyItem {
    private Long banPolicyId;
    private String code;        // BAN_W1 / BAN_W2 / BAN_W3
    private Integer warningNo;
    private String banType;     // temporary / permanent
    private Integer banDays;    // permanent 이면 null
    private String description;
}
