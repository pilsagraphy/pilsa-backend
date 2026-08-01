package com.back.quote.mapper;

import com.back.quote.dto.QuoteDataResponse;
import com.back.quote.dto.QuoteRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuoteMapper {

    // 1. 문장 등록
    void insertQuote(@Param("request") QuoteRequest request);

    // 2. 문장 수정
    int updateQuote(@Param("quoteId") Long quoteId, @Param("request") QuoteRequest request);

    // 3. 문장 삭제
    int deleteQuote(@Param("quoteId") Long quoteId);

    // 4. 문장 전체 목록 조회 (관리자)
    List<QuoteDataResponse> findAllQuotes();

    // 5. 랜덤 문장 1건 조회 (공개)
    QuoteDataResponse findRandomQuote();
}
