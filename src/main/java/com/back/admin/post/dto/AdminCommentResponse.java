package com.back.admin.post.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 관리자 상세의 댓글 (모든 state 포함 — 블라인드/삭제 댓글도 노출)
@Getter
@Setter
public class AdminCommentResponse {
    private Long commentId;
    private Long parentCommentId;  // 답글이면 부모 댓글. 관리자 상세에서 부모·자식 관계를 보여 주기 위해
    private String content;
    private Long userId;
    private String authorName;
    private Boolean isAnonymous;
    private Boolean isPrivate;
    private String state;          // normal / blind / deleted
    private LocalDateTime created;
    private LocalDateTime updated;
    private ModerationNoteResponse moderation;  // 마지막 조치 (없으면 null)
    private java.util.List<com.back.admin.moderation.revision.ContentRevisionResponse> revisions;  // 이전 본문
}
