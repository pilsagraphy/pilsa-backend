package com.back.admin.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

/** (시, 수) 한 쌍 — 하루의 시간대별 활성 회원 (0~23) */
@Getter
@Setter
public class HourCountRow {
    private int hour;
    private int count;
}
