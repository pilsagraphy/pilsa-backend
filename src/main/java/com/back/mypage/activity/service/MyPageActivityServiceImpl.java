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
        List<MyPostRow> posts = mapper.findMyPosts(userId, boardId, keyword, sort, offset(page, size), size);
        return new MyPostPageResponse(totalPages(totalCount, size), totalCount, posts);
    }

    @Override
    public MyCommentPageResponse getMyComments(int page, int size, Long boardId, String keyword) {
        Long userId = AuthUtils.currentUserId();
        page = PageUtils.clampPage(page);
        size = PageUtils.clampSize(size);
        long totalCount = mapper.countMyComments(userId, boardId, keyword);
        List<MyCommentRow> comments = mapper.findMyComments(userId, boardId, keyword, offset(page, size), size);
        return new MyCommentPageResponse(totalPages(totalCount, size), totalCount, comments);
    }

    @Override
    public MyPostPageResponse getMyLikedPosts(int page, int size, Long boardId, String keyword, String sort) {
        Long userId = AuthUtils.currentUserId();
        page = PageUtils.clampPage(page);
        size = PageUtils.clampSize(size);
        long totalCount = mapper.countMyLikedPosts(userId, boardId, keyword);
        List<MyPostRow> posts = mapper.findMyLikedPosts(userId, boardId, keyword, sort, offset(page, size), size);
        return new MyPostPageResponse(totalPages(totalCount, size), totalCount, posts);
    }

    // page(1-base)·size 는 진입부에서 PageUtils 로 보정된 값만 들어온다.
    // clampPage 는 상한이 없어 큰 page 가 들어오면 int 곱셈이 넘친다 — ?page=30000000&size=100 이면
    // (page-1)*size 가 음수(-1294967396)로 뒤집혀 OFFSET 음수 → MySQL 문법 오류(500)가 된다.
    // long 으로 계산해 상한을 씌워 데이터 범위를 넘은 페이지는 빈 목록으로 응답한다.
    private int offset(int page, int size) {
        long offset = (long) (page - 1) * size;
        return (int) Math.min(offset, Integer.MAX_VALUE);
    }

    private int totalPages(long totalCount, int size) {
        return (int) ((totalCount + size - 1) / size); // ceil
    }
}
