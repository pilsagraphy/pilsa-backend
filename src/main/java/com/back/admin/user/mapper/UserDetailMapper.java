package com.back.admin.user.mapper;

import com.back.admin.user.dto.UserDetailResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 관리자 회원 상세 조회 — 여러 표를 한 회원 기준으로 모은다 */
@Mapper
public interface UserDetailMapper {

    /** 기본 정보 + 활동 수치 + 기기 수 + 끈 글 수 + 제재 요약. 없으면 null */
    UserDetailResponse findBase(@Param("userId") Long userId);

    /** 구글 연동 행 { googleEmail, linkedAt, lastSyncedAt } — 없으면 null */
    Map<String, Object> findGoogleLink(@Param("userId") Long userId);

    /** 간편로그인 연결 목록 [{ provider, email, linkedAt }] — 구글·네이버·카카오 (PM 2026-10-13 "어떤 소셜로 로그인하는지") */
    List<Map<String, Object>> findSocialAccounts(@Param("userId") Long userId);

    /** 네이버 캘린더 { naverEmail, linkedAt, lastSentAt, sentCount } — 없으면 null */
    Map<String, Object> findNaverCalendar(@Param("userId") Long userId);

    /** 카카오 톡캘린더 { kakaoName, linkedAt, lastSyncedAt } — 없으면 null */
    Map<String, Object> findKakaoCalendar(@Param("userId") Long userId);

    /** 비밀번호 없는 소셜 가입 회원인가 (users.password_hash = 'SOCIAL_ONLY') */
    String findPasswordHash(@Param("userId") Long userId);

    /** 최근 N일 접속 — 날짜별 접속 시간대 수 */
    List<UserDetailResponse.DailyCount> findAccessDaily(@Param("userId") Long userId, @Param("days") int days);

    /** 최근 N일 앱 실행 — 날짜별 실행 횟수 */
    List<UserDetailResponse.DailyCount> findAppLaunchDaily(@Param("userId") Long userId, @Param("days") int days);
}
