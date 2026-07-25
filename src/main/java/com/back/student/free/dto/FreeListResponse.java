package com.back.student.free.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 전체조회 게시글 정보
@Getter
@Setter
public class FreeListResponse {
    private Long postId;
    private String title;
    private String authorName;      // 익명 여부에 따라 서비스에서 처리
    private int likeCount;
    private int viewCount;
    private int commentCount;       // 자유게시판 추가
    private String categoryName;    // 자유게시판 추가
    private boolean hasAttachment;
    private LocalDateTime created;
}