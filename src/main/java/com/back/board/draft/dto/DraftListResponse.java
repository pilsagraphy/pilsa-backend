package com.back.board.draft.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 임시저장 목록 응답.
 * count 는 drafts.size() 와 같다 — 시안의 `저장 | n` 카운터를 프론트가 그대로 그릴 수 있게 함께 내려준다.
 */
@Getter
@Setter
public class DraftListResponse {
    private int count;
    private List<DraftListItemResponse> drafts;

    public DraftListResponse(List<DraftListItemResponse> drafts) {
        this.drafts = drafts;
        this.count = drafts == null ? 0 : drafts.size();
    }
}
