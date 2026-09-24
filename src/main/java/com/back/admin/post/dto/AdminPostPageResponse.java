package com.back.admin.post.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 게시글 관리 목록 페이지 응답
@Getter
@Setter
public class AdminPostPageResponse {
    private int totalPages;
    private int totalCount;
    private List<AdminPostListResponse> posts;
}
