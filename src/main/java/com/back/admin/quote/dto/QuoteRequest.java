package com.back.admin.quote.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// 등록/수정 요청
@Getter
@Setter
public class QuoteRequest {
    private String content;
    private LocalDate startDate;
    private LocalDate endDate;

    private Long quoteId;
}
