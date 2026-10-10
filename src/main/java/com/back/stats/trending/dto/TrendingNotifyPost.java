package com.back.stats.trending.dto;

import lombok.Data;

/** 주간 인기 글 알림에 담을 글 한 편 */
@Data
public class TrendingNotifyPost {
    private Long postId;
    private String title;
}
