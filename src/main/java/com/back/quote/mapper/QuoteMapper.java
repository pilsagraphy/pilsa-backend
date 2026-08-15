package com.back.quote.mapper;

import com.back.quote.dto.QuoteDataResponse;
import com.back.quote.dto.QuoteListItemResponse;
import com.back.quote.dto.QuoteRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuoteMapper {

    // 1. 문장 등록
    void insertQuote(@Param("request") QuoteRequest request, @Param("writerId") Long writerId);

    // 2. 문장 수정 (마지막 수정자로 writerId 갱신)
    int updateQuote(@Param("quoteId") Long quoteId, @Param("request") QuoteRequest request, @Param("writerId") Long writerId);

    // 3. 문장 삭제
    int deleteQuote(@Param("quoteId") Long quoteId);

    // 4. 문장 전체 목록 조회 (관리자)
    List<QuoteListItemResponse> findAllQuotes();

    // 5. 랜덤 문장 1건 조회 (공개)
    QuoteDataResponse findRandomQuote();
}
