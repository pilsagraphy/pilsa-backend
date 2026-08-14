package com.back.admin.post.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 관리자 상세의 댓글 (모든 state 포함 — 블라인드/삭제 댓글도 노출)
@Getter
@Setter
public class AdminCommentResponse {
    private Long commentId;
    private String content;
    private Long userId;
    private String authorName;
    private Boolean isAnonymous;
    private Boolean isPrivate;
    private String state;          // normal / blind / deleted
    private LocalDateTime created;
    private LocalDateTime updated;
}
