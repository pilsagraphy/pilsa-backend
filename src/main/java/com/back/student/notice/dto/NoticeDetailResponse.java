package com.back.student.notice.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class NoticeDetailResponse {
    private Long postId;
    private String title;
    private String content;
    private Long userId; // 작성자 ID 필수
    private String authorName; // 이름도 보여줘야 하니 추가하는 게 좋음
    // private int viewCount;
    private LocalDateTime created; // 상세 페이지에도 등록일 필요
    private boolean isPinned;

    private Long prevPostId; // 이전글 ID
    private Long nextPostId; // 다음글 ID

    private List<AttachmentFileResponse> attachments;
    private List<Long> imageIds;
}
