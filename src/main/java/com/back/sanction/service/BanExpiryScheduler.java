package com.back.sanction.service;

import com.back.sanction.mapper.SanctionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BanExpiryScheduler {

    private final SanctionMapper sanctionMapper;

    // 매시간 만료된 임시정지의 ban_status 캐시를 정리 (로그인 판정 자체는 항상 banned_until 실시간 비교가 기준이라
    // 이 스케줄러가 늦게 돌아도 만료된 유저의 로그인은 이미 허용됨 - 이건 관리자 페이지용 캐시 정리 목적)
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void liftExpiredTemporaryBans() {
        List<Long> expiredUserIds = sanctionMapper.findExpiredTemporaryBanUserIds();
        for (Long userId : expiredUserIds) {
            sanctionMapper.updateUserBanStatus(userId, "none", null);
            sanctionMapper.closeActiveBanLog(userId, null);
        }
        if (!expiredUserIds.isEmpty()) {
            log.info("만료된 임시정지 {}건 자동 해제 완료", expiredUserIds.size());
        }
    }
}
