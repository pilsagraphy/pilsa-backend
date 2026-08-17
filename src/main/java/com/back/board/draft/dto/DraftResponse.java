package com.back.board.draft.dto;

import lombok.Getter;

/** 임시저장 단순 메시지 응답 (삭제 등). */
@Getter
public class DraftResponse {
    private final String message;

    public DraftResponse(String message) {
        this.message = message;
    }
}
