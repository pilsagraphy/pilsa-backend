package com.back.board.draft.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 선업로드 대기 첨부 INSERT 홀더 (post_id·draft_id 둘 다 NULL 상태로 저장).
 * INSERT 후 생성된 attachmentId 를 useGeneratedKeys 로 되받는다.
 */
@Getter
@Setter
public class PendingAttachment {
    private Long attachmentId;   // 생성 PK (INSERT 후 채워짐)
    private Long uploadedBy;
    private String originName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;         // MIME
    private String attachmentType;   // file / image
}
