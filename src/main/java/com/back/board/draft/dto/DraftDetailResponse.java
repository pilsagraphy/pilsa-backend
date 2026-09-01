package com.back.board.draft.dto;

import com.back.board.dto.AttachmentFileResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 임시저장 단건 (GET .../drafts/{draftId}) — "이어쓰기"용 전체 복원 데이터.
 *
 * 첨부는 일반 첨부(usage_type='attachment')만 내려준다 —
 * 본문 인라인 이미지(usage_type='inline')는 이미 content 마크다운 안에 URL 로 박혀 있어 중복이다.
 * 날짜는 updatedAt(마지막 저장 시각)만 — 이어쓰기 판단 기준.
 */
@Getter
@Setter
public class DraftDetailResponse {

    private Long draftId;
    private String title;
    private String content;
    private Long categoryId;
    private Boolean isAnonymous;
    private LocalDateTime updatedAt;

    // 일반 첨부 목록 (없으면 빈 목록). 본문 이미지는 제외 — content 에 이미 들어 있음
    private List<AttachmentFileResponse> attachments;
}
