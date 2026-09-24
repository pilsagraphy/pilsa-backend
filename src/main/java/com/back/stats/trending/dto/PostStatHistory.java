package com.back.stats.trending.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 한 게시글의 <b>직전 이력</b> — 이번 구간 이전(stat_hour &lt; 구간시작)의 행에서만 뽑는다.
 *
 * 이번 구간 행을 기준선에서 제외하는 것이 재실행 안전성의 핵심이다:
 * 같은 구간을 다시 돌려도 기준선이 자기 자신으로 바뀌지 않으므로 증가분이 0으로 무너지지 않는다.
 */
@Getter
@Setter
public class PostStatHistory {
    private Long postId;
    private int lastViewCount;      // 가장 최근 행의 누적 조회수 = 이번 증가분의 기준선
    private int lastLikeCount;
    private int lastCommentCount;
    private int windowRowCount;     // 기준선 산출에 쓴 직전 구간 수 (0이면 첫 등장 → spike_ratio NULL)
    private double windowScoreSum;  // 직전 N구간 raw_score 합 (행 평균이 아니라 SUM/N 을 쓰기 위해 합계로 받는다)
}
