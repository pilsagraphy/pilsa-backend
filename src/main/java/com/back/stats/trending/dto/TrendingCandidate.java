package com.back.stats.trending.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 급상승 집계 후보 1건 — 집계 시점의 <b>누적</b> 수치를 담는다.
 * 증가분(delta)은 이 값과 직전 집계 행({@link PostStatHistory})의 차로 계산한다.
 */
@Getter
@Setter
public class TrendingCandidate {
    private Long postId;
    private Long boardId;
    private String readScope;          // 집계 당시 boards.read_scope 스냅샷 (조회 시 권한 필터 근거)
    private LocalDateTime createdAt;    // 글 나이 = 구간 종료 - createdAt → freshness 산출
    private int viewCount;              // posts.view_count (누적)
    private int likeCount;              // post_likes 수
    private int commentCount;           // 공개 댓글 수 (state='normal' AND is_private=0)
}
