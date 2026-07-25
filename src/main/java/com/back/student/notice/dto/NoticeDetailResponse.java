package com.back.student.notice.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

// 단일글 조회
@Getter
@Setter
public class NoticeDetailResponse {
    private Long postId;
    private String title;
    private String content;
    private Long userId; // 작성자 ID 필수
    private String authorName; // 이름도 보여줘야 하니 추가하는 게 좋음
    // private int viewCount;
    private LocalDateTime updated; // 상세 페이지에도 등록일 필요
    private boolean isPinned;

    private int likeCount;      // 좋아요 총 개수
    private boolean isLiked;    // 현재 로그인한 사용자의 좋아요 여부

    private String prevPostApi; // 이전글 API 경로
    private String nextPostApi; // 다음글 API 경로

    private List<AttachmentFileResponse> attachments;
    private int attachmentCount; // 첨부파일 총 개수
}
