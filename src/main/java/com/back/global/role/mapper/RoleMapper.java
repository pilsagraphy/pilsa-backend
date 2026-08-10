package com.back.global.role.mapper;

import com.back.global.role.dto.RoleResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper {
  RoleResponse findMemberInfoByUserId(@Param("userId") Long userId);
}