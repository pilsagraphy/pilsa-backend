package com.back.member.service;

import com.back.member.dto.MemberBanRequest;
import com.back.member.dto.MemberPageResponse;
import com.back.member.dto.MemberResponse;
import com.back.member.dto.MemberSuspendRequest;
import com.back.member.dto.MemberUpdateRequest;

public interface MemberService {

    // 회원 전체 목록 조회 (검색, 정렬, 페이지네이션)
    MemberPageResponse getMembers(int page, int size, String keyword, String sort);

    // 회원 정보 수정 (이름/전화/학번/이메일/재학상태/권한)
    MemberResponse updateMember(Long userId, MemberUpdateRequest request);

    // 회원 정지 (temporary) - 단일 회원, 종료일까지
    MemberResponse suspendMember(Long userId, MemberSuspendRequest request);

    // 회원 영구차단 (permanent) - 단일 또는 다중 회원
    MemberResponse banMembers(MemberBanRequest request);
}
