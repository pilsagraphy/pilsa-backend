package com.back.global.role.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper {
  String findRoleByUserId(@Param("userId") Long userId);
}