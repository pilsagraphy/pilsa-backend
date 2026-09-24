package com.back.stats.signup.mapper;

import com.back.stats.signup.dto.SignupWeekRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 주간 신규가입 스냅샷(stats_signup_weekly).
 *
 * 접속·게시글 통계와 달리 스냅샷을 남기는 이유는 원본이 사라지기 때문이다:
 * 탈퇴 90일 정리 배치가 users 행을 물리 삭제하므로 과거 가입 수치가 소급 감소하고,
 * member_type 도 졸업 시 바뀐다. 지금 고정해 두지 않으면 되돌릴 수 없다.
 */
@Mapper
public interface StatsSignupMapper {

    // 지정 주부터 현재까지를 users 에서 다시 집계 (주 시작일 = 그 주 월요일)
    List<SignupWeekRow> aggregateSignupsSince(@Param("fromWeekStart") LocalDate fromWeekStart);

    // 집계 결과 반영 — 같은 주를 다시 집계하면 값만 갱신된다(멱등)
    void upsertWeeklySignups(@Param("rows") List<SignupWeekRow> rows);
}
