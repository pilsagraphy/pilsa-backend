package com.back.mypage.activity.service;

import com.back.global.security.AuthUtils;
import com.back.mypage.activity.dto.MyCommentPageResponse;
import com.back.mypage.activity.dto.MyCommentRow;
import com.back.mypage.activity.dto.MyPostPageResponse;
import com.back.mypage.activity.dto.MyPostRow;
import com.back.mypage.activity.mapper.MyPageActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageActivityServiceImpl implements MyPageActivityService {

    private final MyPageActivityMapper mapper;

    @Override
    public MyPostPageResponse getMyPosts(int page, int size, Long boardId, String keyword, String sort) {
        Long userId = AuthUtils.currentUserId();
        long totalCount = mapper.countMyPosts(userId, boardId, keyword);
        List<MyPostRow> posts = mapper.findMyPosts(userId, boardId, keyword, sort, offset(page, size), size);
        return new MyPostPageResponse(totalPages(totalCount, size), totalCount, posts);
    }

    @Override
    public MyCommentPageResponse getMyComments(int page, int size, Long boardId, String keyword) {
        Long userId = AuthUtils.currentUserId();
        long totalCount = mapper.countMyComments(userId, boardId, keyword);
        List<MyCommentRow> comments = mapper.findMyComments(userId, boardId, keyword, offset(page, size), size);
        return new MyCommentPageResponse(totalPages(totalCount, size), totalCount, comments);
    }

    @Override
    public MyPostPageResponse getMyLikedPosts(int page, int size, Long boardId, String keyword, String sort) {
        Long userId = AuthUtils.currentUserId();
        long totalCount = mapper.countMyLikedPosts(userId, boardId, keyword);
        List<MyPostRow> posts = mapper.findMyLikedPosts(userId, boardId, keyword, sort, offset(page, size), size);
        return new MyPostPageResponse(totalPages(totalCount, size), totalCount, posts);
    }

    // page(1-base)·size 정규화 후 offset 계산
    private int offset(int page, int size) {
        return (Math.max(page, 1) - 1) * Math.max(size, 1);
    }

    private int totalPages(long totalCount, int size) {
        int s = Math.max(size, 1);
        return (int) ((totalCount + s - 1) / s); // ceil
    }
}
