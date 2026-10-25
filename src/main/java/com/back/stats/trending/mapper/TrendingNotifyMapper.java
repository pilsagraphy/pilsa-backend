package com.back.stats.trending.mapper;

import com.back.stats.trending.dto.TrendingNotifyPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 주간 인기 글 알림(TrendingNotifyBatch) 전용 조회 */
@Mapper
public interface TrendingNotifyMapper {

    Long findBoardIdByName(@Param("name") String name);

    /** 지난 days 일 급상승으로 선정됐던 글 — final_score 높은 순, 글당 1행. 이미 보낸 글 제외 */
    List<TrendingNotifyPost> findTrendingPosts(@Param("boardId") Long boardId, @Param("limit") int limit, @Param("days") int days);

    /** 급상승이 없던 기간의 대안 — 지난 days 일 반응(조회+좋아요×5+댓글×3) 상위. 이미 보낸 글 제외 */
    List<TrendingNotifyPost> findTopReactedPosts(@Param("boardId") Long boardId, @Param("limit") int limit, @Param("days") int days);

    /** activeWithinDays 일 안에 접속했지만 최근 days 일은 미접속인 회원. 탈퇴·영구차단·기기 미등록·이 글을 읽었거나 쓴 회원 제외 */
    List<Long> findInactiveUserIds(@Param("days") int days, @Param("activeWithinDays") int activeWithinDays, @Param("postId") Long postId);

    /** 보낸 글 기록 (trending_notify_log) — 다음 회차부터 다시 고르지 않는다 */
    void insertSentPosts(@Param("postIds") List<Long> postIds);
}
