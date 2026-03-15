package com.back.student.info.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class CommentDetailResponse {
    private Long commentId;
    private String content;
    private String authorName;
    private boolean isPrivate; // 비밀댓글 여부
    private LocalDateTime updated;
    private Long userId;
}