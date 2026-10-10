package com.back.mypage.activity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 마이페이지 잔디 — 날짜별 활동(글+댓글)·접속 수. 각 행 { date: 'yyyy-MM-dd', count } */
@Mapper
public interface MyActivityGridMapper {

    List<Map<String, Object>> findActivityDaily(@Param("userId") Long userId, @Param("days") int days);

    List<Map<String, Object>> findAccessDaily(@Param("userId") Long userId, @Param("days") int days);
}
