package com.back.board.draft.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 임시저장 목록 1행 (GET .../drafts).
 *
 * count 필드는 없다 — 개수는 배열 길이로 센다(시안의 '저장 | N' 카운터 = drafts.length, SPEC-A5).
 * 날짜는 생성일이 아니라 updatedAt(마지막 저장 시각)을 내려준다 — 이어쓰기 판단 기준이기 때문.
 */
@Getter
@Setter
public class DraftListResponse {

    private Long draftId;
    private String title;      // 저장 당시 제목 (없으면 null)
    private String preview;    // 본문 앞 20자 미리보기 (LEFT(content, 20)). 없으면 null
    private Integer attachCnt; // 일반 첨부(attachment_type='file') 개수. 본문 인라인 이미지는 제외
    private LocalDateTime updatedAt;
}
