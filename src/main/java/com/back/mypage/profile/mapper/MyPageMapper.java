package com.back.mypage.profile.mapper;

import com.back.mypage.profile.dto.MyPageBasicInfoRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MyPageMapper {

    // 로그인아이디/이름/가입일
    MyPageBasicInfoRow findBasicInfo(@Param("userId") Long userId);

    // 전체 기간 작성글 수 (state='normal')
    int countMyPosts(@Param("userId") Long userId);

    // 전체 기간 작성한 댓글 수 (state='normal')
    int countMyComments(@Param("userId") Long userId);

    // 전체 기간 좋아요 누른 글 수 (대상 글이 state='normal'인 것만)
    int countMyLikedPosts(@Param("userId") Long userId);

    // 기간 내 작성글 수
    int countMyPostsInPeriod(@Param("userId") Long userId,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 기간 내 작성한 댓글 수
    int countMyCommentsInPeriod(@Param("userId") Long userId,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 기간 내 내 글이 받은 좋아요 수 — post_likes.created_at(좋아요 누른 시점) 기준
    int countMyReceivedLikesInPeriod(@Param("userId") Long userId,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 정책값 단건 조회 (semester1_start_month / semester2_start_month)
    String findPolicySetting(@Param("code") String code);
}
