package com.back.auth.mapper;

import com.back.auth.dto.RoleResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper {
  RoleResponse findMemberInfoByUserId(@Param("userId") Long userId);
}