package com.back.admin.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 급상승 집계 한 행 (stats_post_hourly + 글 제목 · 게시판명) */
@Getter
@Setter
public class TrendingPostResponse {
    private LocalDateTime statHour;
    private Long postId;
    private Long boardId;
    private String boardName;
    private String title;
    private String readScope;
    private Integer rankNo;
    private Boolean isTrending;
    private BigDecimal rawScore;
    private BigDecimal baselineScore;
    private BigDecimal spikeRatio;
    private BigDecimal finalScore;
    private int viewDelta;
    private int likeDelta;
    private int commentDelta;
    private int viewCount;
    private int likeCount;
    private int commentCount;
}
