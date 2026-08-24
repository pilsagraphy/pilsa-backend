package com.back.auth.mapper;

import com.back.auth.dto.RoleResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper {
  RoleResponse findMemberInfoByUserId(@Param("userId") Long userId);

  /** 헤더 "OOO님" 표기용 이름. 탈퇴 회원은 조회되지 않는다(is_deleted 필터) */
  String findNameByUserId(@Param("userId") Long userId);
}