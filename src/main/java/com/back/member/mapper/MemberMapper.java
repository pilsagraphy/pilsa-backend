package com.back.member.mapper;

import com.back.member.dto.MemberListResponse;
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
}
