package com.back.quote.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 조회 응답용 - 문장 하나
@Getter
@Setter
public class QuoteDataResponse {
    private Long quoteId;
    private String content;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long writerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
