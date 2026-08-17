package com.back.admin.quote.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 문장 목록의 한 건 (관리자 문장 관리 화면).
 *
 * 목록은 생성일만 내려준다 — 수정일(updatedAt)은 단건 조회 응답에만 포함한다.
 */
@Getter
@Setter
public class QuoteListItemResponse {
    private Long quoteId;
    private String content;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long writerId;
    private LocalDateTime createdAt;
}
