package com.back.board.draft.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 임시저장 목록 한 줄 (시안의 `저장 | n` 슬롯 목록).
 * 본문 전체 대신 미리보기(preview)만 내려간다 — 목록에서 전체 HTML 을 그릴 필요가 없기 때문.
 */
@Getter
@Setter
public class DraftListItemResponse {
    private Long draftId;
    private Integer slotNo;        // 1~5
    private String title;          // 제목 (없으면 null)
    private String preview;        // 본문 앞부분 미리보기 (태그 제거는 프론트/서버 중 서버가 간단 처리)
    private Integer attachCnt;     // 일반 첨부(attachment_type='file') 개수
    private LocalDateTime updatedAt;
}
