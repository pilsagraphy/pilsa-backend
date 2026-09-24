package com.back.board.attachment.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * attachments 테이블 1행 (내부 전용 — 응답으로 나가지 않는다).
 * 선업로드 INSERT 파라미터와 파일 조회 권한 판정에 함께 쓴다.
 *
 * 파일 조회는 경로에 boardId 가 없어도 파일 → 글 → 게시판을 조인해 read_scope 를 판정해야 하므로
 * 글 상태(postState)와 게시판 id(boardId)까지 한 번에 가져온다.
 */
@Getter
@Setter
public class AttachmentFileRow {

    private Long attachmentId;
    private Long postId;        // NULL = 아직 글에 연결되지 않은 선업로드 파일
    private Long uploaderId;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private String usageType;   // attachment / inline
    private Long fileSize;
    private String state;       // 첨부 자체의 상태 normal / deleted
    private String postState;   // 연결된 글의 상태 (선업로드면 null)
    private Long boardId;       // 연결된 글의 게시판 (선업로드면 null)
}
