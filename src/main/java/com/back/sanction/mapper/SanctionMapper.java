package com.back.sanction.mapper;

import com.back.sanction.dto.BanPolicyDto;
import com.back.sanction.dto.ModerationLogDto;
import com.back.sanction.dto.PenaltyLogDto;
import com.back.sanction.dto.SanctionedUserResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SanctionMapper {

    // 조치 이력(append-only) 기록 - 삭제/복원 등. 생성된 actionId는 dto.actionId로 채워짐
    void insertModerationLog(ModerationLogDto dto);

    // 주의 포인트 원장 기록
    void insertPenaltyLog(PenaltyLogDto dto);

    // 현재 유효한(회수되지 않고 만료되지 않은) 주의 포인트 합계
    int sumValidCautionPoints(@Param("userId") Long userId);

    // 경고 원장 기록
    void insertWarningLog(@Param("userId") Long userId, @Param("expiresAt") LocalDateTime expiresAt);

    // 현재 유효한(만료되지 않은) 경고 개수
    int countValidWarnings(@Param("userId") Long userId);

    // 몇 번째 경고에 어떤 차단이 적용되는지 정책 조회
    BanPolicyDto findBanPolicyByWarningNo(@Param("warningNo") int warningNo);

    // 차단 이력 기록
    void insertBanLog(@Param("userId") Long userId,
                       @Param("warningNo") int warningNo,
                       @Param("banType") String banType,
                       @Param("startsAt") LocalDateTime startsAt,
                       @Param("endsAt") LocalDateTime endsAt);

    // users 캐시 컬럼(ban_status/banned_until) 갱신
    void updateUserBanStatus(@Param("userId") Long userId,
                              @Param("banStatus") String banStatus,
                              @Param("bannedUntil") LocalDateTime bannedUntil);

    // 정책 수치 조회 (policy_settings.code -> setting_value)
    String findPolicySetting(@Param("code") String code);

    // 현재 제재 중인 회원 목록 (관리자 페이지용)
    List<SanctionedUserResponse> findSanctionedUsers();

    // 특정 회원의 태그/제재 정보 단건 조회
    SanctionedUserResponse findSanctionedUserById(@Param("userId") Long userId);

    // 활성 상태인 최신 ban_log 행을 해제 처리 (liftedBy가 null이면 시스템 자동 해제)
    void closeActiveBanLog(@Param("userId") Long userId, @Param("liftedBy") Long liftedBy);

    // 만료된 임시정지 대상 유저 ID 목록 (스케줄러용)
    List<Long> findExpiredTemporaryBanUserIds();
}
