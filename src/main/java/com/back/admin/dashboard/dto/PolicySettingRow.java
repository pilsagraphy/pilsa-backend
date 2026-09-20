package com.back.admin.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/** policy_settings 한 줄 */
@Getter
@Setter
public class PolicySettingRow {
    private String code;
    private String settingValue;
    private String description;
}
