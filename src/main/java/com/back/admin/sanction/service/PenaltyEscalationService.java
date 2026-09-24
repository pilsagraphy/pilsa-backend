package com.back.admin.sanction.service;

import com.back.admin.sanction.dto.BanPolicyDto;
import com.back.admin.sanction.mapper.SanctionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 주의 포인트 누적 → 경고 전환 → 정지/영구차단 에스컬레이션 서비스.
 *
 * moderation_log / penalty_log 기록 자체는 admin.moderation(ModerationService)이 담당하고,
 * 이 서비스는 "벌점이 부과된 뒤" 누적 결과에 따른 경고/차단 전환만 처리한다.
 * (PR #57 moderation 공통 모듈 + PR #68 제재 파이프라인 결합 — 중복 INSERT 제거)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PenaltyEscalationService {

    private final SanctionMapper sanctionMapper;

    private static final String DEFAULT_CAUTION_PER_WARNING = "10";
    private static final String DEFAULT_WARNING_TTL_DAYS = "365";

    private int policyInt(String code, String defaultValue) {
        String value = sanctionMapper.findPolicySetting(code);
        return Integer.parseInt(value != null ? value : defaultValue);
    }

    // 현재 유효한(미회수·미만료) 주의 포인트 합계 - 벌점 부과 직전 값을 저장해 두는 용도
    public int currentValidCautionSum(Long userId) {
        return sanctionMapper.sumValidCautionPoints(userId);
    }

    /**
     * 벌점(penalty_log) 부과 직후 호출. 유효 주의 합계가 경고 기준(기본 10점)의 배수를
     * 넘어설 때마다 경고를 발행하고, 경고 횟수에 매칭되는 ban_policy(1주/1달/영구)를 적용한다.
     *
     * @param userId 벌점을 받은 회원
     * @param oldValidSum 벌점 부과 "직전"의 유효 주의 합계 (경계 통과 횟수 계산용)
     */
    @Transactional
    public void escalateAfterPenalty(Long userId, int oldValidSum) {
        int cautionPerWarning = policyInt("cautions_per_warning", DEFAULT_CAUTION_PER_WARNING);
        int warningTtlDays = policyInt("warning_ttl_days", DEFAULT_WARNING_TTL_DAYS);

        int newSum = sanctionMapper.sumValidCautionPoints(userId);
        int warningsToIssue = (newSum / cautionPerWarning) - (oldValidSum / cautionPerWarning);

        for (int i = 0; i < warningsToIssue; i++) {
            sanctionMapper.insertWarningLog(userId, LocalDateTime.now().plusDays(warningTtlDays));
            int currentWarningNo = sanctionMapper.countValidWarnings(userId);

            BanPolicyDto policy = sanctionMapper.findBanPolicyByWarningNo(currentWarningNo);
            if (policy == null) {
                // 정의된 차단 정책보다 큰 경고 단계(예: 이미 영구차단된 유저)는 추가 조치 없이 무시
                log.info("경고 {}회 도달, 매칭되는 차단 정책 없음 (userId={})", currentWarningNo, userId);
                continue;
            }

            LocalDateTime startsAt = LocalDateTime.now();
            LocalDateTime endsAt = "permanent".equals(policy.getBanType())
                    ? null
                    : startsAt.plusDays(policy.getBanDays());

            // 기존 활성 차단이 남아있으면 먼저 닫는다 (활성 ban_log 행은 항상 최대 1개 유지 - 상위 제재로 대체)
            sanctionMapper.closeActiveBanLog(userId, null);
            sanctionMapper.insertBanLog(userId, policy.getWarningNo(), policy.getBanType(), startsAt, endsAt);
            sanctionMapper.updateUserBanStatus(userId, policy.getBanType(), endsAt);

            log.warn("유저 {} 제재 적용 - 경고 {}회, banType={}, endsAt={}",
                    userId, currentWarningNo, policy.getBanType(), endsAt);
        }
    }
}
