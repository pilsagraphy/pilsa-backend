package com.back.user.service;

import com.back.user.dto.UserBanRequest;
import com.back.user.dto.UserPageResponse;
import com.back.user.dto.UserResponse;
import com.back.user.dto.UserSuspendRequest;
import com.back.user.dto.UserUpdateRequest;

public interface UserService {

    // 회원 전체 목록 조회 (검색, 정렬, 페이지네이션)
    UserPageResponse getUsers(int page, int size, String keyword, String sort);

    // 회원 정보 수정 (이름/전화/학번/이메일/재학상태/권한)
    UserResponse updateUser(Long userId, UserUpdateRequest request);

    // 회원 정지 (temporary) - 단일 회원, 종료일까지
    UserResponse suspendUser(Long userId, UserSuspendRequest request);

    // 회원 영구차단 (permanent) - 단일 또는 다중 회원
    UserResponse banUsers(UserBanRequest request);
}
