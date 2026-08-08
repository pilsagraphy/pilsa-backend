package com.back.member.mapper;

import com.back.member.dto.MemberListResponse;
import com.back.member.dto.MemberUpdateRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemberMapper {

    // 회원 전체 목록 조회 (검색, 정렬, 페이지네이션 포함)
    List<MemberListResponse> findMembers(
            @Param("keyword") String keyword,
            @Param("sort") String sort,
            @Param("offset") int offset,
            @Param("size") int size
    );

    // 회원 총 개수 조회 (페이지 계산용, 검색 조건 반영)
    long countMembers(@Param("keyword") String keyword);

    // 활성(미탈퇴) 회원 존재 여부 - 수정 대상 확인용
    boolean existsActiveMember(@Param("userId") Long userId);

    // 이메일 중복 확인 (본인 제외) - uq_users_email
    boolean existsEmailExcludingUser(@Param("email") String email, @Param("userId") Long userId);

    // 전화번호 중복 확인 (본인 제외) - uq_users_phone
    boolean existsPhoneExcludingUser(@Param("phone") String phone, @Param("userId") Long userId);

    // 학번 중복 확인 (본인 제외) - uq_users_student_no
    boolean existsStudentNoExcludingUser(@Param("studentNo") String studentNo, @Param("userId") Long userId);

    // 회원 정보 수정 (전달된 필드만 동적 수정)
    int updateMember(@Param("userId") Long userId, @Param("req") MemberUpdateRequest req);
}
