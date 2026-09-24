package com.back.board.draft.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 임시저장 단순 결과 메시지 응답 (저장/덮어쓰기/삭제).
 * 신규 저장처럼 생성된 draftId 를 돌려줘야 할 때만 draftId 가 채워진다(없으면 JSON 에서 생략).
 */
@Getter
@Setter
@NoArgsConstructor
public class DraftResponse {

    private String message;

    // 신규 저장(POST) 응답 전용 — 프론트가 이후 PUT 덮어쓰기에 쓸 id
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long draftId;

    public DraftResponse(String message) {
        this.message = message;
    }

    public DraftResponse(String message, Long draftId) {
        this.message = message;
        this.draftId = draftId;
    }
}
