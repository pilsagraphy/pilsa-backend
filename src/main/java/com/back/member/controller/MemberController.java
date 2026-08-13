package com.back.member.controller;

import com.back.member.dto.MemberBanRequest;
import com.back.member.dto.MemberPageResponse;
import com.back.member.dto.MemberResponse;
import com.back.member.dto.MemberSuspendRequest;
import com.back.member.dto.MemberUpdateRequest;
import com.back.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // 회원 정보 수정 (이름/전화/학번/이메일 + 재학상태/권한) - 관리자 전용
    @PutMapping("/api/admin/members/{userId}")
    public ResponseEntity<MemberResponse> updateMember(
            @PathVariable Long userId,
            @RequestBody MemberUpdateRequest request) {
        log.info("회원 정보 수정 요청 - userId: {}, 데이터: {}", userId, request);
        return ResponseEntity.ok(memberService.updateMember(userId, request));
    }

    // 회원 정지 (temporary) - 단일 회원, 종료일까지 - 관리자 전용
    @PostMapping("/api/admin/members/{userId}/suspend")
    public ResponseEntity<MemberResponse> suspendMember(
            @PathVariable Long userId,
            @RequestBody MemberSuspendRequest request) {
        log.info("회원 정지 요청 - userId: {}, 데이터: {}", userId, request);
        return ResponseEntity.ok(memberService.suspendMember(userId, request));
    }

    // 회원 영구차단 (permanent) - 단일/다중 회원 - 관리자 전용
    @PostMapping("/api/admin/members/ban")
    public ResponseEntity<MemberResponse> banMembers(@RequestBody MemberBanRequest request) {
        log.info("회원 영구차단 요청 - 데이터: {}", request);
        return ResponseEntity.ok(memberService.banMembers(request));
    }
}
