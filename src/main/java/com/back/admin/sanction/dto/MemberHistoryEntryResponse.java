package com.back.admin.sanction.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 제재 회원 관리 화면 — 회원 한 명의 글/댓글에 일어난 일 한 건 (신고 접수 · 블라인드 · 삭제 · 복원).
 *
 * 예전에는 "신고 1건 = 행 1개" 라 신고와 그 처리가 한 행에 묶였고, 관리자가 신고 없이 바로 조치한 건은
 * 신고 행에 끼워 넣어야 했다. PM 요청(2026-09-27)으로 사건 단위로 풀었다:
 *   report  — 회원 신고 접수 (reports_log, source=user)
 *   blind   — 블라인드 (moderation_log applied_state=blind, acted_by 비면 신고 누적 자동)
 *   delete  — 삭제      (moderation_log applied_state=deleted). isDirect=true 면 블라인드를 거치지 않은 즉시 삭제
 *   restore — 복원      (moderation_log applied_state=normal)
 * 관리자 직접 조치를 신고 관리에 올린 reports_log 행(source=admin)은 moderation_log 와 같은 사건이라 내려보내지 않는다.
 * 게시글·댓글 공용이다 — 댓글이면 commentId 가 채워지고 title 은 원글 제목이다.
 */
@Data
public class MemberHistoryEntryResponse {
    private String eventType;          // report / blind / delete / restore
    private LocalDateTime eventAt;
    private Long reportId;             // report 만
    private String reportStatus;       // report 만: pending / resolved / rejected
    private Long actionId;             // blind / delete / restore 만 (moderation_log.action_id)
    private String reporterName;       // report 만. 탈퇴했으면 null
    private String actorName;          // 조치한 관리자. 자동 블라인드면 null
    private Boolean isAuto;            // 신고 누적 자동 블라인드
    private Boolean isDirect;          // delete 만: 직전에 블라인드가 없었던 즉시 삭제
    private String reasonLabel;
    private String detail;             // 기타 사유일 때 적은 내용

    private Long postId;               // 이동 경로. 댓글이면 소속 게시글
    private Long commentId;            // 댓글일 때만
    private Long boardId;
    private String boardName;
    private String title;              // 게시글 제목 (댓글이면 원글 제목)
    private String preview;
    private String state;              // 대상의 현재 표시 상태 normal / blind / deleted
}
