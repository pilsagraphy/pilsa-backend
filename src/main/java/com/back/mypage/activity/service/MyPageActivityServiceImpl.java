package com.back.mypage.activity.service;

import com.back.global.security.AuthUtils;
import com.back.global.util.PageUtils;
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
        page = PageUtils.clampPage(page);
        size = PageUtils.clampSize(size);
        long totalCount = mapper.countMyPosts(userId, boardId, keyword);
        List<MyPostRow> posts = mapper.findMyPosts(userId, boardId, keyword, sort, PageUtils.offset(page, size), size);
        return new MyPostPageResponse(totalPages(totalCount, size), totalCount, posts);
    }

    @Override
    public MyCommentPageResponse getMyComments(int page, int size, Long boardId, String keyword) {
        Long userId = AuthUtils.currentUserId();
        page = PageUtils.clampPage(page);
        size = PageUtils.clampSize(size);
        long totalCount = mapper.countMyComments(userId, boardId, keyword);
        List<MyCommentRow> comments = mapper.findMyComments(userId, boardId, keyword, PageUtils.offset(page, size), size);
        return new MyCommentPageResponse(totalPages(totalCount, size), totalCount, comments);
    }

    @Override
    public MyPostPageResponse getMyLikedPosts(int page, int size, Long boardId, String keyword, String sort) {
        Long userId = AuthUtils.currentUserId();
        page = PageUtils.clampPage(page);
        size = PageUtils.clampSize(size);
        long totalCount = mapper.countMyLikedPosts(userId, boardId, keyword);
        List<MyPostRow> posts = mapper.findMyLikedPosts(userId, boardId, keyword, sort, PageUtils.offset(page, size), size);
        return new MyPostPageResponse(totalPages(totalCount, size), totalCount, posts);
    }

    private int totalPages(long totalCount, int size) {
        return (int) ((totalCount + size - 1) / size); // ceil
    }
}
