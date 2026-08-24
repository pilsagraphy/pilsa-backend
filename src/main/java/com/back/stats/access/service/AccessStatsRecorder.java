package com.back.stats.access.service;

import com.back.stats.access.mapper.StatsAccessMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접속 기록 — JWT 인증이 성공한 요청에서 호출된다(로그아웃은 기록하지 않는다).
 *
 * 설계상 세 가지를 지킨다:
 *  - <b>요청을 절대 막지 않는다</b>: @Async 로 요청 스레드에서 떼어내고, 실패는 삼켜서 로그만 남긴다.
 *    통계가 서비스 가용성보다 앞설 수 없다.
 *  - <b>요청 트랜잭션에 끼어들지 않는다</b>: REQUIRES_NEW 로 자체 트랜잭션을 쓴다.
 *  - <b>중복은 DB가 정리한다</b>: 같은 회원·같은 시간대는 PK 충돌 → INSERT IGNORE 로 1행 유지.
 *    그래서 매 요청 호출해도 행이 늘지 않고, 애플리케이션 캐시를 둘 필요가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessStatsRecorder {

    private final StatsAccessMapper statsAccessMapper;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            statsAccessMapper.recordAccess(userId);
        } catch (Exception e) {
            // 통계 실패로 인증된 요청이 깨지면 안 된다 — 흔한 원인(DB 순단)은 다음 요청에서 자연 복구된다
            log.warn("접속 통계 기록 실패 - userId={}, 원인={}", userId, e.getMessage());
        }
    }
}
