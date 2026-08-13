package com.back.donation.controller;

import com.back.donation.dto.DonationResponse;
import com.back.donation.exception.DonationException; // 새로 만든 예외 import
import com.back.donation.service.DonationService;
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
@RequestMapping("/api/public/Donation")
@RequiredArgsConstructor
public class DonationController {

    private final DonationService DonationService;

    @GetMapping("/")
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