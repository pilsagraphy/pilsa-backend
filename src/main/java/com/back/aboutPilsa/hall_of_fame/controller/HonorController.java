package com.back.aboutPilsa.hall_of_fame.controller;

import com.back.aboutPilsa.hall_of_fame.dto.HonorResponse;
import com.back.aboutPilsa.hall_of_fame.exception.HonorException; // 새로 만든 예외 import
import com.back.aboutPilsa.hall_of_fame.service.HonorService;
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
@RequestMapping("/api/public/honor")
@RequiredArgsConstructor
public class HonorController {

    private final HonorService honorService;

    @GetMapping("/")
    public ResponseEntity<List<HonorResponse>> getAllHonors() {
        log.info("명예의 전당 목록 조회 요청 시작");

        // Service에서 목록을 가져옴
        List<HonorResponse> list = honorService.getHonorList();

        // 데이터가 없는 경우 예외를 던짐
        // 예기치 못한 에러는 GlobalExceptionHandler가 처리하도록 exception만들음
        if (list == null || list.isEmpty()) {
            log.warn("명예의 전당 조회 실패 - 등록된 데이터 없음");
            throw new HonorException("등록된 후원 내역이 없습니다.", HttpStatus.NOT_FOUND);
        }

        log.info("명예의 전당 조회 성공 - 총 {}명의 데이터 반환", list.size());
        return ResponseEntity.ok(list);
    }
}