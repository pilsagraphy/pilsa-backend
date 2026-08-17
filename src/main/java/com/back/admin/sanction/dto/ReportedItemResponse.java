package com.back.admin.sanction.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 신고 관리 목록 한 행 (게시글 신고 / 댓글 신고 공통)
@Getter
@Setter
public class ReportedItemResponse {
    private String targetType;         // 'post' / 'comment'
    private Long targetId;             // 신고 대상 post_id / comment_id
    private Long postId;               // 원문 이동용 게시글 id (post면 targetId와 동일, comment면 소속 게시글)
    private String preview;            // 대상 미리보기 (본문 앞부분)
    private Long boardId;
    private String boardName;          // boards.name (댓글은 소속 게시글의 게시판). 한글 게시판명
    private String authorName;         // 대상 콘텐츠 작성자
    private String reasonLabel;        // 대표 신고 사유 (reasons.label)
    private LocalDateTime firstReportedAt; // 최초 신고일시 = MIN(created_at)
    private int reportCount;           // 누적 신고 건수
    private String reportStatus;       // 대표 신고 상태 pending/rejected/resolved
    private String state;              // 대상의 현재 표시 상태 normal/blind/deleted
}
