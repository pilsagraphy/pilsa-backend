package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 단일글 상세조회 (공지/자유/정보 통합).
 * 게시판마다 의미 없는 필드는 기본값으로 내려간다.
 *  - isAnonymous : 자유게시판 전용
 *  - isPinned    : 공지사항 전용
 *  - categoryName / comments : 공지사항은 미사용(빈 값/빈 목록)
 */
@Getter
@Setter
public class BoardDetailResponse {
    private Long postId;
    private String title;
    private String content;
    private Long userId;            // 작성자 ID (본인 확인용)
    private String authorName;
    private LocalDateTime updated;
    private Boolean isAnonymous;    // 자유게시판 익명 여부
    private Boolean isPinned;       // 공지사항 중요표시 여부
    private String categoryName;

    private int likeCount;
    private Boolean isLiked;

    private String prevPostApi;
    private String nextPostApi;

    private List<AttachmentFileResponse> attachments;
    private int attachmentCount;

    private List<CommentDetailResponse> comments;
}
