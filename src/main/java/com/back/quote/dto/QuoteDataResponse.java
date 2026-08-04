package com.back.quote.dto;

import lombok.Getter;
import lombok.Setter;

// 조회 응답용 - 문장 하나
@Getter
@Setter
public class QuoteDataResponse {
    private Long quoteId;
    private String content;
    private String startDate;
    private String endDate;
    private Long writerId;
    private String createdAt;
    private String updatedAt;
}
