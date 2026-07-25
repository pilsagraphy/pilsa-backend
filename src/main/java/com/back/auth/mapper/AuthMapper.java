package com.back.auth.mapper;

import com.back.auth.dto.FindIdVerifyRequest;
import com.back.auth.dto.UserDto;
import com.back.auth.dto.UserSignupDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {
    // 로그인
    UserDto findByLoginId(@Param("loginId") String loginId);
    void updateLastLoginAt(@Param("loginId") String loginId);
    // jti 회전
//    void rotateSessionKey(String oldJti, String newJti);
    // 회원가입
    void insertUser(UserSignupDto user);
    // 회원가입 - 아이디 & 이메일 중복 확인
    boolean existsByLoginId(@Param("loginId") String loginId);
    boolean existsByEmail(@Param("email") String email);

    // 아이디 찾기 전용 - 인증 완료된 이메일에 한해 loginId 반환
    String findLoginIdByEmail(String email);
    // 비밀번호 초기화 전 단계 - 아이디/이메일 일치 확인
    boolean existsByLoginIdAndEmail(@Param("loginId") String loginId, @Param("email") String email);
    // 비밀번호 초기화
    void updatePassword(@Param("loginId") String loginId, @Param("password") String encodedNewPassword);
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