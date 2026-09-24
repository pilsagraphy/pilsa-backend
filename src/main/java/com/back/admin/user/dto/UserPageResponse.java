package com.back.admin.user.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 회원 목록 전체 조회 - 페이지 응답 (페이지 넘기기용 메타 포함)
@Getter
@Setter
public class UserPageResponse {
    private int totalPages;   // 전체 페이지 수
    private long totalCount;  // 전체 회원 수(검색 조건 반영)
    private List<UserListResponse> members;   // 응답 계약(PR #60)은 'members' 유지
}
