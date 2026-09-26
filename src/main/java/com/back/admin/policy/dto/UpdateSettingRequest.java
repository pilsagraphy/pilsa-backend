package com.back.admin.policy.dto;

import lombok.Getter;
import lombok.Setter;

/** PUT /api/admin/policies/settings/{code} 본문 */
@Getter
@Setter
public class UpdateSettingRequest {
    private String settingValue;   // 필수. varchar(100)
    private String description;    // 선택. 주면 설명도 바꾼다
}
