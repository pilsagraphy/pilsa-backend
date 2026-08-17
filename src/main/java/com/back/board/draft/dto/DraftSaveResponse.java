package com.back.board.draft.dto;

import lombok.Getter;

/**
 * 임시저장 저장/덮어쓰기 응답.
 * 신규 저장이면 프론트가 draftId 를 보관해 두었다가 다음 저장부터 PUT(덮어쓰기)로 보낸다.
 */
@Getter
public class DraftSaveResponse {
    private final String message;
    private final Long draftId;
    private final Integer slotNo;

    public DraftSaveResponse(String message, Long draftId, Integer slotNo) {
        this.message = message;
        this.draftId = draftId;
        this.slotNo = slotNo;
    }
}
