package com.back.board.draft.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 선업로드(업로드 대기) 첨부 INSERT 파라미터 겸 생성키 홀더.
 *
 * insertPendingAttachment 가 useGeneratedKeys 로 생성된 attachment_id 를 {@link #attachmentId} 에 담아준다
 * (BoardRequest.postId / DraftRequest.draftId 와 동일한 패턴 — 불변 Long 파라미터로는 키를 되돌려 받을 수 없다).
 */
@Getter
@Setter
public class PendingAttachment {

    private Long uploadedBy;
    private String originName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;       // MIME 타입 (image/png 등)
    private String attachmentType; // 'file' / 'image'
    private Long attachmentId;     // [out] 생성된 PK

    public PendingAttachment(Long uploadedBy, String originName, String fileUrl,
                             Long fileSize, String fileType, String attachmentType) {
        this.uploadedBy = uploadedBy;
        this.originName = originName;
        this.fileUrl = fileUrl;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.attachmentType = attachmentType;
    }
}
