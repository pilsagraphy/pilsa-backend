package com.back.admin.post.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 관리자 게시글 상세 (state 무관 조회 — 블라인드/삭제 글도 열람, 댓글·첨부 포함)
@Getter
@Setter
public class AdminPostDetailResponse {
    private Long postId;
    private Long boardId;
    private String boardName;
    private String categoryName;   // 카테고리 미사용 게시판이면 null
    private String title;
    private String content;
    private Long authorId;
    private String authorName;     // 익명글도 실제 작성자명
    private Boolean isAnonymous;
    private Boolean isPinned;
    private int viewCount;
    private int likeCount;
    private int commentCount;      // 전체(모든 state) 기준
    private String state;          // normal / blind / deleted
    private LocalDateTime created;
    private LocalDateTime updated;
    private List<AdminAttachmentResponse> attachments;
    private List<AdminCommentResponse> comments;   // 블라인드/삭제 댓글까지 포함
}
