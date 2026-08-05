package com.back.admin.post.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 게시글 관리 목록 한 행
@Getter
@Setter
public class AdminPostListResponse {
    private Long postId;
    private Long boardId;
    private String boardCode;    // boards.code (영문 식별자). 한글 표기는 프론트에서 매핑
    private String title;
    private String authorName;   // 관리자 화면에는 익명글도 실제 작성자명 표시
    private int commentCount;
    private int likeCount;
    private int viewCount;
    private LocalDateTime created;
    private String state;        // normal / blind (deleted 는 목록에서 제외)
}
