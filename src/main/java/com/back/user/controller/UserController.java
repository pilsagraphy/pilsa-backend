package com.back.user.controller;

import com.back.user.dto.UserBanRequest;
import com.back.user.dto.UserPageResponse;
import com.back.user.dto.UserResponse;
import com.back.user.dto.UserSuspendRequest;
import com.back.user.dto.UserUpdateRequest;
import com.back.user.service.UserService;
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
public class UserController {

    private final UserService userService;

    // 회원 전체 목록 조회 (검색, 정렬, 페이지네이션) - 관리자 전용
    @GetMapping("/api/admin/members")
    public ResponseEntity<UserPageResponse> getUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", defaultValue = "latest") String sort) {
        log.info("회원 목록 조회 요청 - page: {}, size: {}, keyword: {}, sort: {}", page, size, keyword, sort);
        UserPageResponse response = userService.getUsers(page, size, keyword, sort);
        log.info("회원 목록 조회 성공 - 총 {}명, 현재 페이지 {}건", response.getTotalCount(), response.getMembers().size());
        return ResponseEntity.ok(response);
    }

    // 회원 정보 수정 (이름/전화/학번/이메일 + 재학상태/권한) - 관리자 전용
    @PutMapping("/api/admin/members/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long userId,
            @RequestBody UserUpdateRequest request) {
        log.info("회원 정보 수정 요청 - userId: {}, 데이터: {}", userId, request);
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    // 회원 정지 (temporary) - 단일 회원, 종료일까지 - 관리자 전용
    @PostMapping("/api/admin/members/{userId}/suspend")
    public ResponseEntity<UserResponse> suspendUser(
            @PathVariable Long userId,
            @RequestBody UserSuspendRequest request) {
        log.info("회원 정지 요청 - userId: {}, 데이터: {}", userId, request);
        return ResponseEntity.ok(userService.suspendUser(userId, request));
    }

    // 회원 영구차단 (permanent) - 단일/다중 회원 - 관리자 전용
    @PostMapping("/api/admin/members/ban")
    public ResponseEntity<UserResponse> banUsers(@RequestBody UserBanRequest request) {
        log.info("회원 영구차단 요청 - 데이터: {}", request);
        return ResponseEntity.ok(userService.banUsers(request));
    }
}
