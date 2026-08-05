package com.back.quote.service;

import com.back.quote.dto.QuoteDataResponse;
import com.back.quote.dto.QuoteListResponse;
import com.back.quote.dto.QuoteRequest;
import com.back.quote.dto.QuoteResponse;

public interface QuoteService {

    // 1. 문장 등록 (Admin)
    QuoteResponse createQuote(QuoteRequest request);

    // 2. 문장 수정 (Admin)
    QuoteResponse updateQuote(Long quoteId, QuoteRequest request);

    // 3. 문장 삭제 (Admin)
    QuoteResponse deleteQuote(Long quoteId);

    // 4. 문장 전체 목록 조회 (Admin)
    QuoteListResponse getAllQuotes();

    // 5. 랜덤 문장 조회 (Public)
    QuoteDataResponse getRandomQuote();
}
