package com.back.admin.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** (날짜, 수) 한 쌍 — 일별 활성 회원 · 일별 앱 실행 집계 공용 매퍼 행 */
@Getter
@Setter
public class DateCountRow {
    private LocalDate statDate;
    private int count;
}
