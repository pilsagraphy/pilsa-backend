package com.back.mypage.calendar.mapper;

import com.back.mypage.calendar.dto.NaverCalendarLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NaverCalendarMapper {

    NaverCalendarLink findByUserId(@Param("userId") Long userId);

    /** 팬아웃 대상 — 탈퇴 회원 제외 */
    List<NaverCalendarLink> findAllLinked();

    int upsertLink(NaverCalendarLink link);

    int updateAccessToken(@Param("userId") Long userId, @Param("accessToken") byte[] accessToken, @Param("expiresIn") int expiresIn);

    int bumpSent(@Param("userId") Long userId, @Param("count") int count);

    int deleteByUserId(@Param("userId") Long userId);
}
