package com.back.auth.core.mapper;

import com.back.auth.local.dto.FindIdVerifyRequest;
import com.back.auth.core.dto.UserDto;
import com.back.auth.local.dto.UserSignupDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {
    // 로그인
    UserDto findByLoginId(@Param("loginId") String loginId);
    // 구글 소셜 로그인 — 아이디/비밀번호 없이 userId 로 계정을 집어 토큰을 발급한다
    UserDto findByUserId(@Param("userId") Long userId);
    void updateLastLoginAt(@Param("loginId") String loginId);
    void updateLastLoginAtByUserId(@Param("userId") Long userId);
    // jti 회전
//    void rotateSessionKey(String oldJti, String newJti);
    // 회원가입
    void insertUser(UserSignupDto user);
    // 회원가입 - 아이디 & 이메일 중복 확인
    boolean existsByLoginId(@Param("loginId") String loginId);
    boolean existsByStudentNo(@Param("studentNo") String studentNo);

    boolean existsByPhone(@Param("phone") String phone);

    boolean existsByEmail(@Param("email") String email);

    // 아이디 찾기 전용 - 인증 완료된 이메일에 한해 loginId 반환
    String findLoginIdByEmail(String email);
    // 비밀번호 초기화 전 단계 - 아이디/이메일 일치 확인
    boolean existsByLoginIdAndEmail(@Param("loginId") String loginId, @Param("email") String email);
    // 비밀번호 초기화
    void updatePassword(@Param("loginId") String loginId, @Param("password") String encodedNewPassword);
    // 모든 기기에서 로그아웃 — token_version 을 올려 그 사용자의 기존 토큰을 전부 무효화한다
    void bumpTokenVersion(@Param("userId") Long userId);

    // 이메일 찾기 - 학번+이름이 모두 일치하는 사용자의 이메일 조회
    String findEmailByStudentNoAndName(@Param("studentNo") String studentNo, @Param("name") String name);

    // 정책값 단건 조회 (mail_verified_ttl_minutes 등) — 없으면 null
    String findPolicySetting(@Param("code") String code);
}
