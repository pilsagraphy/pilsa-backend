package com.back.mypage.profile.mapper;

import com.back.mypage.profile.dto.ProfileResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProfileMapper {
  ProfileResponse findMemberInfoByUserId(@Param("userId") Long userId);
}