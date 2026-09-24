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

    // 처리(관리자 조치) 쪽 사유. 신고 사유와 별개다 — 신고는 '기타'로 들어왔는데 관리자는 '욕설'로 지웠을 수 있다.
    // 신고 없이 관리자가 바로 조치한 건은 reportId 가 null 이고 이 셋만 채워진다.
    private String actionState;       // blind / deleted (적용한 상태)
    private String actionReasonLabel; // 조치 사유 라벨
    private String actionDetail;      // 기타일 때 관리자가 적은 내용
    private String reporterName;      // 신고자 이름 (신고를 거친 건만). 운영진에게는 숨기지 않는다
    private String actorName;         // 조치한 관리자 이름. 자동 블라인드면 null
    private Boolean isAuto;           // 신고 누적 자동 블라인드였는가 (acted_by 가 비어 있다)
}
