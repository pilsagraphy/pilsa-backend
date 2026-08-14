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
import com.back.global.security.AuthUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteServiceImpl implements QuoteService {

    private final QuoteMapper quoteMapper;

    // 관리자 권한 확인 (공통 유틸 사용)
    private void checkAdminRole() {
        if (!AuthUtils.isAdmin()) {
            throw new QuoteException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }
    }

    private static final int CONTENT_MAX_LENGTH = 500; // DB content 컬럼(VARCHAR(500))과 동일하게 유지

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new QuoteException("문장 내용은 필수 입력 항목입니다.", HttpStatus.BAD_REQUEST);
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new QuoteException("문장 내용은 " + CONTENT_MAX_LENGTH + "자를 초과할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    // 노출 기간(startDate ~ endDate) 선후 관계 검증
    private void validateExposurePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new QuoteException("노출 시작일이 종료일보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    // 로그인한 사용자의 id 추출 (공통 유틸 사용)
    private Long getCurrentUserId() {
        return AuthUtils.currentUserId();
    }

    @Override
    @Transactional
    public QuoteResponse createQuote(QuoteRequest request) {
        checkAdminRole();
        validateContent(request.getContent());

        // startDate/endDate는 선택값: 미입력 시 오늘 ~ 오늘+7일로 자동 설정
        if (request.getStartDate() == null) {
            request.setStartDate(LocalDate.now());
        }
        if (request.getEndDate() == null) {
            request.setEndDate(LocalDate.now().plusDays(7));
        }
        validateExposurePeriod(request.getStartDate(), request.getEndDate());

        Long writerId = getCurrentUserId();
        quoteMapper.insertQuote(request, writerId);

        Map<String, Object> data = new HashMap<>();
        data.put("quoteId", request.getQuoteId());
        data.put("content", request.getContent());
        data.put("startDate", request.getStartDate());
        data.put("endDate", request.getEndDate());
        data.put("writerId", writerId);

        return new QuoteResponse("이주의 문장이 등록되었습니다.", data);
    }

    @Override
    @Transactional
    public QuoteResponse updateQuote(Long quoteId, QuoteRequest request) {
        checkAdminRole();
        validateContent(request.getContent());
        if (request.getStartDate() != null && request.getEndDate() != null) {
            validateExposurePeriod(request.getStartDate(), request.getEndDate());
        }

        Long writerId = getCurrentUserId();
        int updated = quoteMapper.updateQuote(quoteId, request, writerId);
        if (updated == 0) {
            throw new QuoteException("해당 문장을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("quoteId", quoteId);
        data.put("content", request.getContent());
        data.put("startDate", request.getStartDate());
        data.put("endDate", request.getEndDate());
        data.put("writerId", writerId);

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
