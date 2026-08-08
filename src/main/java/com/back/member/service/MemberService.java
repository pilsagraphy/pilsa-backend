package com.back.member.service;

import com.back.member.dto.MemberPageResponse;

public interface MemberService {

    // 회원 전체 목록 조회 (검색, 정렬, 페이지네이션)
    MemberPageResponse getMembers(int page, int size, String keyword, String sort);
}
