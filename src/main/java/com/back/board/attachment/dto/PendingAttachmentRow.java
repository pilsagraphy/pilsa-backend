package com.back.board.attachment.dto;

import lombok.Getter;
import lombok.Setter;

/** 정리 배치 대상(글에 연결되지 않은 채 보존기간이 지난 선업로드 파일) 1행 */
@Getter
@Setter
public class PendingAttachmentRow {

    private Long attachmentId;
    private String fileUrl;
}
