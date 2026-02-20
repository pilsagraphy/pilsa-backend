package com.back.auth.mapper;

import com.back.auth.dto.UserDto;
import com.back.auth.dto.UserSignupDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {
    // 로그인
    UserDto findByLoginId(@Param("loginId") String loginId);
    // 회원가입
    void insertUser(UserSignupDto user);
    // 회원가입 - 아이디 & 이메일 중복 확인
    boolean existsByLoginId(@Param("loginId") String loginId);
    boolean existsByEmail(@Param("email") String email);
}

//@Mapper
//public interface AuthMapper {
//  // email로 회원 찾기
//  UserDto findByEmail(@Param("email") String email);
//  // phone으로 회원 찾기
//  UserDto findByPhone(@Param("phone") String phone);
//
//  // 회원가입 (미승인 회원 추가)
//  void insertUser(UserDto user);
//
//  // 비밀번호 업데이트
//  void updatePassword(@Param("id") Long id,
//                      @Param("password") String encodedPassword);
//}