package com.back.member.controller;

import com.back.member.dto.MemberPageResponse;
import com.back.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // 회원 전체 목록 조회 (검색, 정렬, 페이지네이션) - 관리자 전용
    @GetMapping("/api/admin/members")
    public ResponseEntity<MemberPageResponse> getMembers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", defaultValue = "latest") String sort) {
        log.info("회원 목록 조회 요청 - page: {}, size: {}, keyword: {}, sort: {}", page, size, keyword, sort);
        MemberPageResponse response = memberService.getMembers(page, size, keyword, sort);
        log.info("회원 목록 조회 성공 - 총 {}명, 현재 페이지 {}건", response.getTotalCount(), response.getMembers().size());
        return ResponseEntity.ok(response);
    }
}
