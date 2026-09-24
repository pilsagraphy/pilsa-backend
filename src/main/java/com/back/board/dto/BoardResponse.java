package com.back.board.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 게시글 등록/수정/삭제/좋아요 등 단순 결과 메시지 응답.
// 등록처럼 생성된 id 를 돌려줘야 할 때만 postId 가 채워진다 (없으면 JSON 에서 생략).
@Getter
@Setter
@NoArgsConstructor
public class BoardResponse {

    private String message;

    // 등록 응답 전용 — 등록 직후 상세 페이지로 이동하려면 프론트가 새 글 id 를 알아야 한다
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long postId;

    public BoardResponse(String message) {
        this.message = message;
    }

    public BoardResponse(String message, Long postId) {
        this.message = message;
        this.postId = postId;
    }
}
