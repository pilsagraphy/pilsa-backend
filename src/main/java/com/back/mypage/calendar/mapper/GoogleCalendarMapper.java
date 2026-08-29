package com.back.mypage.calendar.mapper;

import com.back.mypage.calendar.dto.GoogleCalendarLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoogleCalendarMapper {

    GoogleCalendarLink findByUserId(@Param("userId") Long userId);

    /** 일정 변경 팬아웃 대상 (연동이 살아 있는 사용자 전원). */
    List<GoogleCalendarLink> findAllLinked();

    int upsertLink(GoogleCalendarLink link);

    int updateLastSyncedAt(@Param("userId") Long userId);

    int deleteByUserId(@Param("userId") Long userId);
}
