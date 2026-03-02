package com.back.student.info.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InfoListResponse {
    private Long postId;
    private String title;
    private String authorName; // 실명으로 처리됨
    private int likeCount;
    private int viewCount;
    private int commentCount;
    private String categoryName;
    private boolean hasAttachment;
    private LocalDateTime created;
}