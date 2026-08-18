package com.back.stats.access.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 접속 원본(stats_access_hourly) — 일·주·월·학기·연 통계는 전부 이 테이블 GROUP BY 로 산출한다.
 * 단위별 집계 테이블을 두지 않는 이유는 우리 규모에서 GROUP BY 한 번이면 충분하기 때문이다.
 */
@Mapper
public interface StatsAccessMapper {

    // 같은 회원·같은 시간대는 1행 (PK 충돌은 무시). 시간 버킷은 DB 시각으로 절삭한다
    void recordAccess(@Param("userId") Long userId);

    // 구간 접속자 수 — 급상승 점수의 분모. 컬럼으로 저장하지 않고 필요할 때 집계한다
    int countActiveUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 보존기간 경과 행 물리 삭제 (통계는 소프트삭제 대전제의 예외)
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
