package com.back.admin.dashboard.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 대시보드 "최근 신고" 한 행 (게시글/댓글 신고 통합, 최신순)
@Getter
@Setter
public class RecentReportResponse {
    private String targetType;      // 'post' / 'comment'
    private String boardName;       // boards.name (댓글은 소속 게시글의 게시판)
    private String title;           // 대상 게시글 제목 (댓글 신고는 소속 게시글의 제목)
    private LocalDateTime reportedAt;
}
