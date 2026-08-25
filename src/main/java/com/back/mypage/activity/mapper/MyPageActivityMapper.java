package com.back.mypage.activity.mapper;

import com.back.mypage.activity.dto.MyCommentRow;
import com.back.mypage.activity.dto.MyPostRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MyPageActivityMapper {

    // 탭 1: 내가 쓴 글
    List<MyPostRow> findMyPosts(@Param("userId") Long userId, @Param("boardId") Long boardId,
                                @Param("keyword") String keyword, @Param("sort") String sort,
                                @Param("offset") int offset, @Param("size") int size);

    long countMyPosts(@Param("userId") Long userId, @Param("boardId") Long boardId,
                      @Param("keyword") String keyword);

    // 탭 2: 내가 쓴 댓글 (대댓글 포함)
    List<MyCommentRow> findMyComments(@Param("userId") Long userId, @Param("boardId") Long boardId,
                                      @Param("keyword") String keyword,
                                      @Param("offset") int offset, @Param("size") int size);

    long countMyComments(@Param("userId") Long userId, @Param("boardId") Long boardId,
                         @Param("keyword") String keyword);

    // 탭 3: 좋아요 누른 글
    List<MyPostRow> findMyLikedPosts(@Param("userId") Long userId, @Param("boardId") Long boardId,
                                     @Param("keyword") String keyword, @Param("sort") String sort,
                                     @Param("offset") int offset, @Param("size") int size);

    long countMyLikedPosts(@Param("userId") Long userId, @Param("boardId") Long boardId,
                           @Param("keyword") String keyword);
}
