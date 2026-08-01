package com.back.quote.service;

import com.back.quote.dto.QuoteDataResponse;
import com.back.quote.dto.QuoteListResponse;
import com.back.quote.dto.QuoteRequest;
import com.back.quote.dto.QuoteResponse;
import com.back.quote.exception.QuoteException;
import com.back.quote.mapper.QuoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteServiceImpl implements QuoteService {

    private final QuoteMapper quoteMapper;

    // 관리자 권한 확인만 공통으로 사용
    private void checkAdminRole() {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new QuoteException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new QuoteException("문장 내용은 필수 입력 항목입니다.", HttpStatus.BAD_REQUEST);
        }
    }

    // 노출 기간(startDate ~ endDate) 선후 관계 검증
    private void validateExposurePeriod(String startDate, String endDate) {
        if (startDate == null || endDate == null) {
            throw new QuoteException("노출 시작일과 종료일은 필수 입력 항목입니다.", HttpStatus.BAD_REQUEST);
        }

        if (startDate.compareTo(endDate) > 0) {
            throw new QuoteException("노출 시작일이 종료일보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional
    public QuoteResponse createQuote(QuoteRequest request) {
        checkAdminRole();
        validateContent(request.getContent());
        validateExposurePeriod(request.getStartDate(), request.getEndDate());

        quoteMapper.insertQuote(request);

        Map<String, Object> data = new HashMap<>();
        data.put("quoteId", request.getQuoteId());
        data.put("content", request.getContent());
        data.put("startDate", request.getStartDate());
        data.put("endDate", request.getEndDate());

        return new QuoteResponse("이주의 문장이 등록되었습니다.", data);
    }

    @Override
    @Transactional
    public QuoteResponse updateQuote(Long quoteId, QuoteRequest request) {
        checkAdminRole();
        validateContent(request.getContent());
        validateExposurePeriod(request.getStartDate(), request.getEndDate());

        int updated = quoteMapper.updateQuote(quoteId, request);
        if (updated == 0) {
            throw new QuoteException("해당 문장을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("quoteId", quoteId);
        data.put("content", request.getContent());
        data.put("startDate", request.getStartDate());
        data.put("endDate", request.getEndDate());

        return new QuoteResponse("문장이 성공적으로 수정되었습니다.", data);
    }

    @Override
    @Transactional
    public QuoteResponse deleteQuote(Long quoteId) {
        checkAdminRole();

        int deleted = quoteMapper.deleteQuote(quoteId);
        if (deleted == 0) {
            throw new QuoteException("삭제할 문장을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        return new QuoteResponse("문장이 정상적으로 삭제되었습니다.");
    }

    @Override
    public QuoteListResponse getAllQuotes() {
        checkAdminRole();

        List<QuoteDataResponse> quotes = quoteMapper.findAllQuotes();
        return new QuoteListResponse("문장 목록을 성공적으로 불러왔습니다.", quotes);
    }

    @Override
    public QuoteDataResponse getRandomQuote() {
        QuoteDataResponse quote = quoteMapper.findRandomQuote();
        if (quote == null) {
            throw new QuoteException("현재 노출 기간에 해당하는 문장이 없습니다.", HttpStatus.NOT_FOUND);
        }

        return quote;
    }
}
