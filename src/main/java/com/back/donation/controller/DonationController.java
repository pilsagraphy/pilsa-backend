package com.back.donation.controller;

import com.back.donation.dto.DonationResponse;
import com.back.donation.exception.DonationException; // 새로 만든 예외 import
import com.back.donation.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/donations")
@RequiredArgsConstructor
@Tag(name = "명예의전당",
        description = "명예의전당(후원자) 페이지. 비로그인도 열람 가능한 공개 API.")
public class DonationController {

    private final DonationService DonationService;

    @Operation(summary = "후원자 전체 목록 (공개)",
            description = """
                    명예의전당 페이지 진입 시 호출한다. 비로그인 열람 가능(SecurityConfig permitAll).
                    익명 후원이면 displayName 이 서버에서 "익명후원자"로 치환되어 내려간다.

                    ### 요청 예시
                    ```
                    GET /api/donations
                    ```
                    쿼리 없음.

                    ### 응답 예시
                    ```json
                    [
                      {
                        "donationId": 1, "amount": 100000,
                        "displayName": "홍길동", "affiliation": "13기",
                        "major": "컴퓨터공학과", "message": "응원합니다",
                        "donatedAt": "2026-02-20T10:00:00",
                        "isAnonymous": false, "photoUrl": "/uploads/honor/uuid.png"
                      }
                    ]
                    ```

                    실패: 404 {"message":"등록된 후원 내역이 없습니다."} (후원 데이터가 하나도 없을 때)
                    """)
    @GetMapping
    public ResponseEntity<List<DonationResponse>> getAllDonations() {
        log.info("명예의 전당 목록 조회 요청 시작");

        // Service에서 목록을 가져옴
        List<DonationResponse> list = DonationService.getDonationList();

        // 데이터가 없는 경우 예외를 던짐
        // 예기치 못한 에러는 GlobalExceptionHandler가 처리하도록 exception만들음
        if (list == null || list.isEmpty()) {
            log.warn("명예의 전당 조회 실패 - 등록된 데이터 없음");
            throw new DonationException("등록된 후원 내역이 없습니다.", HttpStatus.NOT_FOUND);
        }

        log.info("명예의 전당 조회 성공 - 총 {}명의 데이터 반환", list.size());
        return ResponseEntity.ok(list);
    }
}
