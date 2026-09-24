package com.back.stats.trending.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// stats_post_hourly 적재 행 (구간 집계 + 급상승 판정 결과)
@Getter
@Setter
public class PostStatRow {
    private LocalDateTime statHour;   // 구간 시작
    private Long postId;
    private Long boardId;
    private String readScope;
    private int viewCount;            // 누적 (다음 구간의 기준선)
    private int likeCount;
    private int commentCount;
    private int viewDelta;
    private int likeDelta;            // 좋아요 취소 시 음수
    private int commentDelta;         // 댓글 삭제 시 음수
    private double rawScore;
    private double baselineScore;     // 직전 N구간 raw_score 의 SUM/N
    private Double spikeRatio;        // null = 이력 없음(관문2 면제)
    private double freshness;
    private double finalScore;
    private Integer rankNo;
    private Boolean isTrending;       // primitive 금지 규칙: Boolean isXxx
}
