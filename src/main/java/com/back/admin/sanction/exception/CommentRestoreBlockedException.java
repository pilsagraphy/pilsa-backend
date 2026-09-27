package com.back.admin.sanction.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 댓글 복원을 막음 — 원 게시글이 공개 상태가 아니다.
 *
 * 삭제·블라인드된 글 아래 댓글만 살리면 아무도 볼 수 없으므로 게시글부터 복원하게 한다.
 * 관리자가 바로 이동할 수 있게 원글 id · 상태 · 누가 지웠는지(작성자 직접 / 관리자 조치) · 작성자를 함께 담는다 (PM, 2026-09-27).
 */
@Getter
public class CommentRestoreBlockedException extends ReportAdminException {

    public static final String CODE = "POST_NOT_VISIBLE";

    private final Long postId;
    private final String postState;   // blind / deleted
    private final String deletedBy;   // author / admin
    private final Long authorId;
    private final String authorName;

    public CommentRestoreBlockedException(Long postId, String postState, String deletedBy, Long authorId, String authorName) {
        super(buildMessage(postState, deletedBy), HttpStatus.CONFLICT);
        this.postId = postId;
        this.postState = postState;
        this.deletedBy = deletedBy;
        this.authorId = authorId;
        this.authorName = authorName;
    }

    private static String buildMessage(String postState, String deletedBy) {
        String stateText = "blind".equals(postState) ? "블라인드" : "삭제";
        String byText = "admin".equals(deletedBy) ? "관리자 조치로" : "작성자가 직접";
        return "원 게시글이 " + byText + " " + stateText + "된 상태라 댓글을 복원할 수 없습니다. 게시글을 먼저 복원해 주세요.";
    }
}
