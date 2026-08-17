package com.back.admin.sanction.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 회원별 신고 내역 중 '댓글' 신고 1건 (제재 회원 관리 화면).
 *
 * 댓글은 제목이 없고, 이동 경로가 댓글이 아니라 소속 게시글이라 게시글 신고와 응답을 나눴다.
 */
@Data
public class ReportedCommentResponse {
    private Long reportId;
    private Long commentId;       // 신고 대상 댓글
    private Long postId;          // 댓글이 달린 게시글 = 이동 경로
    private Long boardId;
    private String boardName;     // boards.name (한글 게시판명)
    private String postTitle;     // 원글 제목 (어느 글의 댓글인지 표시)
    private String preview;       // 댓글 내용 앞부분
    private String state;         // 대상의 현재 표시 상태 normal / blind / deleted
    private Long reasonId;
    private String reasonLabel;
    private String detail;
    private String status;        // pending / rejected / resolved
    private Integer activeFlag;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
