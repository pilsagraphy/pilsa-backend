package com.back.admin.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 급상승 집계 + 판정 기준. 화면이 미선정 행마다 "어느 관문에 걸렸는지" 적으려면 기준값이 필요하다
 * (PM 2026-10-10 밤 "1 과 1위의 차이를 모르겠다"). 값은 policy_settings 의 지금 값이라 과거 행은 당시 기준과 다를 수 있다.
 */
@Data
@AllArgsConstructor
public class TrendingReportResponse {
    private double minScore;
    private double spikeRatio;
    private int topN;
    private List<TrendingPostResponse> rows;
}
