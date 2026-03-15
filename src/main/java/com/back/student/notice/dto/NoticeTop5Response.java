package com.back.student.notice.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

// 공지사항 최신 5개 조회
@Getter
@Setter
public class NoticeTop5Response {
    private Long postId;
    private String title;
    private boolean isPinned;
} // 글 id, 제목, 중요여부
