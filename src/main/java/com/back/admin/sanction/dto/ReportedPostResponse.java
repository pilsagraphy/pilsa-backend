package com.back.admin.sanction.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 회원별 신고 내역 중 '게시글' 신고 1건 (제재 회원 관리 화면).
 *
 * 게시글 신고와 댓글 신고는 화면에서 보여줄 내용(제목 유무, 이동 경로)이 달라 응답을 나눴다.
 * targetType 분기는 프론트가 아니라 API 경로가 담당한다.
 */
@Data
public class ReportedPostResponse {
    private Long reportId;
    private Long postId;          // 신고 대상 게시글 = 이동 경로
    private Long boardId;
    private String boardName;     // boards.name (한글 게시판명)
    private String title;         // 신고된 게시글 제목
    private String preview;       // 본문 앞부분
    private String state;         // 대상의 현재 표시 상태 normal / blind / deleted
    private Long reasonId;
    private String reasonLabel;
    private String detail;        // 기타 사유일 때 신고자가 적은 내용
    private String status;        // pending / rejected / resolved
    private Integer activeFlag;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    // 처리(관리자 조치) 쪽 사유. 신고 사유와 별개다 — 신고는 '기타'로 들어왔는데 관리자는 '욕설'로 지웠을 수 있다.
    // 신고 없이 관리자가 바로 조치한 건은 reportId 가 null 이고 이 셋만 채워진다.
    private String actionState;       // blind / deleted (적용한 상태)
    private String actionReasonLabel; // 조치 사유 라벨
    private String actionDetail;      // 기타일 때 관리자가 적은 내용
}
