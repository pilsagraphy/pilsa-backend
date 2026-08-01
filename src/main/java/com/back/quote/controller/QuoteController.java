package com.back.quote.controller;

import com.back.quote.dto.*;
import com.back.quote.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    // 1. 이주의 문장 등록 (POST) - 201 Created
    @PostMapping("/api/admin/quotes")
    public ResponseEntity<QuoteResponse> createQuote(@RequestBody QuoteRequest request) {
        log.info("문장 등록 요청 데이터: {}", request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(quoteService.createQuote(request));
    }

    // 2. 이주의 문장 수정 (PUT) - 200 OK
    @PutMapping("/api/admin/quotes/{quoteId}")
    public ResponseEntity<QuoteResponse> updateQuote(
            @PathVariable Long quoteId,
            @RequestBody QuoteRequest request) {
        log.info("문장 수정 요청 - ID: {}, 데이터: {}", quoteId, request);
        return ResponseEntity.ok(quoteService.updateQuote(quoteId, request));
    }

    // 3. 이주의 문장 삭제 (DELETE) - 200 OK
    @DeleteMapping("/api/admin/quotes/{quoteId}")
    public ResponseEntity<QuoteResponse> deleteQuote(@PathVariable Long quoteId) {
        log.info("문장 삭제 요청 - ID: {}", quoteId);
        return ResponseEntity.ok(quoteService.deleteQuote(quoteId));
    }

    // 4. 이주의 문장 전체 목록 조회 (GET) - 200 OK (관리자 관리 화면용)
    @GetMapping("/api/admin/quotes")
    public ResponseEntity<QuoteListResponse> getAllQuotes() {
        log.info("문장 전체 목록 조회 요청");
        return ResponseEntity.ok(quoteService.getAllQuotes());
    }

    // 5. 랜덤 문장 조회 (GET) - 200 OK (새로고침마다 랜덤 노출되는 공개 API)
    @GetMapping("/api/public/quotes/random")
    public ResponseEntity<QuoteDataResponse> getRandomQuote() {
        log.info("랜덤 문장 조회 요청");
        return ResponseEntity.ok(quoteService.getRandomQuote());
    }
}
