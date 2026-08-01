package com.back.quote.dto;

import lombok.Getter;
import lombok.Setter;

// 등록/수정 요청
@Getter
@Setter
public class QuoteRequest {
    private String content;
    private String startDate;
    private String endDate;

    private Long quoteId;
}
