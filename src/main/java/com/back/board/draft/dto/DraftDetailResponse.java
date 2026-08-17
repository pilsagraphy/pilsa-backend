package com.back.board.draft.dto;

import com.back.board.dto.AttachmentFileResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 임시저장 단건 불러오기 응답 (이어쓰기용 전체 복원).
 *
 * content(HTML) 안에는 본문 이미지가 <img src="/files/{attachmentId}"> 로 이미 들어 있으므로,
 * attachments 에는 **일반 첨부(attachment_type='file')만** 담는다 — 이미지는 중복 노출하지 않는다.
 */
@Getter
@Setter
public class DraftDetailResponse {
    private Long draftId;
    private Integer slotNo;
    private Long boardId;
    private String title;
    private String content;        // 본문 HTML (이미지 <img> 포함)
    private Long categoryId;
    private Boolean isAnonymous;
    private LocalDateTime updatedAt;

    // 일반 첨부 목록 (본문 이미지는 제외 — content HTML 에 이미 박혀 있음)
    private List<AttachmentFileResponse> attachments;
}
