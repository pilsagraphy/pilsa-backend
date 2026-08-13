package com.back.user.mapper;

import com.back.user.dto.UserListResponse;
import com.back.user.dto.UserUpdateRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {

    // 회원 전체 목록 조회 (검색, 정렬, 페이지네이션 포함)
    List<UserListResponse> findUsers(
            @Param("keyword") String keyword,
            @Param("sort") String sort,
            @Param("offset") int offset,
            @Param("size") int size
    );

    // 회원 총 개수 조회 (페이지 계산용, 검색 조건 반영)
    long countUsers(@Param("keyword") String keyword);

    // 활성(미탈퇴) 회원 존재 여부 - 수정 대상 확인용
    boolean existsActiveUser(@Param("userId") Long userId);

    // 이메일 중복 확인 (본인 제외) - uq_users_email
    boolean existsEmailExcludingUser(@Param("email") String email, @Param("userId") Long userId);

    // 전화번호 중복 확인 (본인 제외) - uq_users_phone. 저장된 값의 하이픈을 제거한 숫자끼리 비교
    boolean existsPhoneDigitsExcludingUser(@Param("phoneDigits") String phoneDigits, @Param("userId") Long userId);

    // 학번 중복 확인 (본인 제외) - uq_users_student_no
    boolean existsStudentNoExcludingUser(@Param("studentNo") String studentNo, @Param("userId") Long userId);

    // 회원 정보 수정 (전달된 필드만 동적 수정)
    int updateUser(@Param("userId") Long userId, @Param("req") UserUpdateRequest req);

    // 현재 차단 상태 캐시 조회 (none / temporary / permanent) - 정지 전 영구차단 여부 확인용
    String findBanStatus(@Param("userId") Long userId);

    // 차단 이력 기록 (단일 - 정지용). endsAt = null 이면 영구
    void insertBanLog(
            @Param("userId") Long userId,
            @Param("warningNo") int warningNo,
            @Param("banType") String banType,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt
    );

    // users 차단 상태 캐시 갱신 (단일 - 정지용). bannedUntil = null 이면 영구
    void updateUserBanStatus(
            @Param("userId") Long userId,
            @Param("banStatus") String banStatus,
            @Param("bannedUntil") LocalDateTime bannedUntil
    );

    // 주어진 id 중 실제 존재하는(미탈퇴) 회원 id 목록 - 영구차단 대상 일괄 검증용
    List<Long> findActiveUserIds(@Param("ids") List<Long> ids);

    // 차단 이력 일괄 기록 (영구차단용 - 여러 회원, 공통 startsAt/banType/warningNo)
    void insertBanLogBatch(
            @Param("userIds") List<Long> userIds,
            @Param("warningNo") int warningNo,
            @Param("banType") String banType,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt
    );

    // users 차단 상태 캐시 일괄 갱신 (영구차단용 - 여러 회원)
    void updateUserBanStatusByIds(
            @Param("userIds") List<Long> userIds,
            @Param("banStatus") String banStatus,
            @Param("bannedUntil") LocalDateTime bannedUntil
    );
}
