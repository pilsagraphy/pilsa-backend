package com.back.student.free.dto;

import com.back.student.free.dto.AttachmentFileResponse;
import com.back.student.free.dto.CommentDetailResponse;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

// 단일글 상세조회
@Getter
@Setter
public class FreeDetailResponse {
    private Long postId;
    private String title;
    private String content;
    private Long userId;            // 작성자 ID 필수
    private String authorName;
    private LocalDateTime updated;
    private boolean isAnonymous;    // 익명 여부
    private String categoryName;

    private int likeCount;
    private boolean isLiked;

    private String prevPostApi;
    private String nextPostApi;

    private List<AttachmentFileResponse> attachments;
    private int attachmentCount;

    private List<CommentDetailResponse> comments; // 댓글 리스트 추가
}