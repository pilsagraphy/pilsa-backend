package com.back.admin.quote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 등록/수정/삭제 응답용
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object data; // 단일 객체(id, content 등)를 담는 용도

    public QuoteResponse(String message) {
        this.message = message;
    }
}
