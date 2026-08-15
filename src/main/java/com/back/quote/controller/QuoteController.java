package com.back.quote.controller;

import com.back.quote.dto.*;
import com.back.quote.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "이 주의 문장",
        description = "메인페이지 '이 주의 문장' 영역. 공개 조회 1개(/api/quotes/current) + 관리자 관리 4개(/api/admin/quotes/**).")
public class QuoteController {

    private final QuoteService quoteService;

    // 1. 이주의 문장 등록 (POST) - 201 Created
    @Operation(summary = "문장 등록 (관리자)",
            description = """
                    관리자 문장 관리 화면에서 새 문장을 노출기간과 함께 등록할 때 호출한다. 성공 시 201 Created.

                    ### 요청 예시
                    ```json
                    {
                      "content": "오늘 쓴 한 문장이 내일의 나를 만든다.",
                      "startDate": "2026-08-17",
                      "endDate": "2026-08-23"
                    }
                    ```
                    - startDate/endDate: 노출기간, 'YYYY-MM-DD' 문자열

                    ### 응답 예시
                    ```json
                    {"message":"문장이 등록되었습니다.","data":{"quoteId":10}}
                    ```
                    (201 Created)

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 필요)
                    """)
    @PostMapping("/api/admin/quotes")
    public ResponseEntity<QuoteResponse> createQuote(@RequestBody QuoteRequest request) {
        log.info("문장 등록 요청 데이터: {}", request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(quoteService.createQuote(request));
    }

    // 2. 이주의 문장 수정 (PUT) - 200 OK
    @Operation(summary = "문장 수정 (관리자)",
            description = """
                    관리자 문장 관리 화면에서 문장 내용이나 노출기간을 고칠 때 호출한다.

                    ### 요청 예시
                    ```json
                    {
                      "content": "수정된 문장",
                      "startDate": "2026-08-17",
                      "endDate": "2026-08-23"
                    }
                    ```

                    ### 응답 예시
                    ```json
                    {"message":"문장이 수정되었습니다.","data":null}
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 필요)
                    """)
    @PutMapping("/api/admin/quotes/{quoteId}")
    public ResponseEntity<QuoteResponse> updateQuote(
            @Parameter(description = "수정할 문장 ID", example = "10")
            @PathVariable Long quoteId,
            @RequestBody QuoteRequest request) {
        log.info("문장 수정 요청 - ID: {}, 데이터: {}", quoteId, request);
        return ResponseEntity.ok(quoteService.updateQuote(quoteId, request));
    }

    // 3. 이주의 문장 삭제 (DELETE) - 200 OK
    @Operation(summary = "문장 삭제 (관리자, 소프트)",
            description = """
                    관리자 문장 관리 화면의 삭제 버튼에서 호출한다. 물리 삭제가 아니라 소프트삭제(state=deleted)로
                    처리되어 목록·랜덤 노출에서 제외된다.

                    ### 요청 예시
                    ```
                    PATCH /api/admin/quotes/10/delete
                    ```
                    본문 없음.

                    ### 응답 예시
                    ```json
                    {"message":"문장이 삭제되었습니다.","data":null}
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 필요)
                    """)
    @PatchMapping("/api/admin/quotes/{quoteId}/delete")
    public ResponseEntity<QuoteResponse> deleteQuote(
            @Parameter(description = "삭제할 문장 ID", example = "10")
            @PathVariable Long quoteId) {
        log.info("문장 삭제 요청 - ID: {}", quoteId);
        return ResponseEntity.ok(quoteService.deleteQuote(quoteId));
    }

    // 4. 이주의 문장 전체 목록 조회 (GET) - 200 OK (관리자 관리 화면용)
    @Operation(summary = "문장 전체 목록 (관리자)",
            description = """
                    관리자 문장 관리 화면 진입 시 호출한다. 등록된 문장을 노출기간과 함께 전부 보여준다.

                    ### 요청 예시
                    ```
                    GET /api/admin/quotes
                    ```
                    쿼리 없음.

                    ### 응답 예시
                    ```json
                    {
                      "quotes": [
                        {
                          "quoteId": 6, "content": "문장",
                          "startDate": "2026-08-10", "endDate": "2026-08-16",
                          "writerId": 1,
                          "createdAt": "2026-08-10T09:00:00", "updatedAt": "2026-08-10T09:00:00"
                        }
                      ]
                    }
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 필요)
                    """)
    @GetMapping("/api/admin/quotes")
    public ResponseEntity<QuoteListResponse> getAllQuotes() {
        log.info("문장 전체 목록 조회 요청");
        return ResponseEntity.ok(quoteService.getAllQuotes());
    }

    // 5. 랜덤 문장 조회 (GET) - 200 OK (새로고침마다 랜덤 노출되는 공개 API)
    @Operation(summary = "이 주의 문장 조회 (공개)",
            description = """
                    메인페이지 접속·새로고침 시 호출한다. 노출기간(startDate~endDate)이 오늘을 포함하는 문장 중
                    랜덤 1건을 내려준다. 비로그인 열람 가능(SecurityConfig permitAll).

                    ### 요청 예시
                    ```
                    GET /api/quotes/current
                    ```
                    쿼리 없음.

                    ### 응답 예시
                    ```json
                    {"content":"바다는 비에 젖지 않는다."}
                    ```
                    """)
    @GetMapping("/api/quotes/current")
    public ResponseEntity<QuoteDataResponse> getRandomQuote() {
        log.info("랜덤 문장 조회 요청");
        return ResponseEntity.ok(quoteService.getRandomQuote());
    }
}
