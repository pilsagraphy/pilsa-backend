package com.back.student.free.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

// 상세페이지 댓글 정보
@Getter
@Setter
public class CommentDetailResponse {
    private Long commentId;
    private String content;
    private String authorName;
    private boolean isAnonymous;
    private LocalDateTime updated;
    private Long userId;           // 본인 확인용
}