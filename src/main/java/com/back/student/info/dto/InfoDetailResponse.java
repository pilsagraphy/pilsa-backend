package com.back.student.info.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class InfoDetailResponse {
    private Long postId;
    private String title;
    private String content;
    private Long userId;
    private String authorName;
    private LocalDateTime updated;
    private String categoryName;

    private int likeCount;
    private boolean isLiked;

    private String prevPostApi;
    private String nextPostApi;

    private List<AttachmentFileResponse> attachments;
    private int attachmentCount;

    private List<CommentDetailResponse> comments;
}