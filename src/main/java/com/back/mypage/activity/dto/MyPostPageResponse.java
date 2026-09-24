package com.back.mypage.activity.dto;

import java.util.List;
import lombok.Getter;

// '내가 쓴 글' / '좋아요 누른 글' 페이지 응답
@Getter
public class MyPostPageResponse {
    private final int totalPages;
    private final long totalCount;   // 필터 반영된 총 개수(페이지네이션용)
    private final List<MyPostRow> posts;

    public MyPostPageResponse(int totalPages, long totalCount, List<MyPostRow> posts) {
        this.totalPages = totalPages;
        this.totalCount = totalCount;
        this.posts = posts;
    }
}
