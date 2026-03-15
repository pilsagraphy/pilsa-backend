package com.back.student.notice.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 공지사항 전체조회 - 글 정보
@Getter
@Setter
public class NoticeListResponse {
    private Long postId;            // 게시글 ID (post_id)
    private String title;           // 제목 (title)
    private String authorName;      // 글쓴이 (users 테이블과 JOIN하여 name 가져옴) // 안보이긴 함
    //private int commentCount;       // 댓글수 (comments 테이블 COUNT) -공지사항엔 없음 다른게시판에 넣을 것
    private int likeCount;          // 좋아요수 (post_likes 테이블 COUNT)
    private int viewCount;          // 조회수 (view_count)
    private boolean isPinned;       // 중요표시 (is_pinned: 1이면 중요)
    private boolean hasAttachment;  // 첨부파일 여부 (attachments 테이블 존재 여부)
    private LocalDateTime created; // 등록일 (created_at)
}