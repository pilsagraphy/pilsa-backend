package com.back.admin.dashboard.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 최근 신고 한 행 (GET /api/admin/dashboard/recent-reports). 게시글/댓글 신고 통합, 대상 단위 최신순.
@Getter
@Setter
public class RecentReportResponse {
    private String targetType;          // 'post' / 'comment'
    private Long targetId;              // 신고 대상 id (post_id 또는 comment_id)
    private Long postId;                // 원글 id — 댓글 신고 행에서 원글로 이동하는 데 필요
    private Long boardId;
    private String boardName;           // boards.name (댓글은 원글의 게시판)
    private String preview;             // 대상 본문 앞 30자
    private LocalDateTime createdAt;    // 해당 대상의 최근 신고 시각
}
