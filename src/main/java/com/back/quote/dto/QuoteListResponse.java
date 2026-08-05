package com.back.quote.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 조회 응답용 - 관리자용 문장 전체 목록
@Getter
@Setter
@AllArgsConstructor
public class QuoteListResponse {
    private String message;
    private List<QuoteDataResponse> quotes;
}
