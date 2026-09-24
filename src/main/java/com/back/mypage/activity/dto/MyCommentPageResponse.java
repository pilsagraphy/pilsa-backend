package com.back.mypage.activity.dto;

import java.util.List;
import lombok.Getter;

// '내가 쓴 댓글' 페이지 응답
@Getter
public class MyCommentPageResponse {
    private final int totalPages;
    private final long totalCount;
    private final List<MyCommentRow> comments;

    public MyCommentPageResponse(int totalPages, long totalCount, List<MyCommentRow> comments) {
        this.totalPages = totalPages;
        this.totalCount = totalCount;
        this.comments = comments;
    }
}
