package com.back.stats.trending.mapper;

import com.back.stats.trending.dto.TrendingNotifyPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 주간 인기 글 알림(TrendingNotifyBatch) 전용 조회 */
@Mapper
public interface TrendingNotifyMapper {

    Long findBoardIdByName(@Param("name") String name);

    /** 지난 7일 급상승으로 선정됐던 글 — final_score 높은 순, 글당 1행 */
    List<TrendingNotifyPost> findTrendingPosts(@Param("boardId") Long boardId, @Param("limit") int limit);

    /** 급상승이 없던 주의 대안 — 지난 7일 반응(조회+좋아요×5+댓글×3) 상위 */
    List<TrendingNotifyPost> findTopReactedPosts(@Param("boardId") Long boardId, @Param("limit") int limit);

    /** N일 이상 접속 기록이 없는 회원 (기록이 아예 없는 회원 포함). 탈퇴·차단 제외 */
    List<Long> findInactiveUserIds(@Param("days") int days);
}
