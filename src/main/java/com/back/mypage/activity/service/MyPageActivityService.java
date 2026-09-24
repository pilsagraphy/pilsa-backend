package com.back.mypage.activity.service;

import com.back.mypage.activity.dto.MyCommentPageResponse;
import com.back.mypage.activity.dto.MyPostPageResponse;

public interface MyPageActivityService {

    // 내가 쓴 글
    MyPostPageResponse getMyPosts(int page, int size, Long boardId, String keyword, String sort);

    // 내가 쓴 댓글 (대댓글 포함)
    MyCommentPageResponse getMyComments(int page, int size, Long boardId, String keyword);

    // 좋아요 누른 글
    MyPostPageResponse getMyLikedPosts(int page, int size, Long boardId, String keyword, String sort);
}
