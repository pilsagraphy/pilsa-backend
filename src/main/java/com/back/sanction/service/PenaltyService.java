package com.back.sanction.service;

import com.back.sanction.dto.BanPolicyDto;
import com.back.sanction.dto.ModerationLogDto;
import com.back.sanction.dto.PenaltyLogDto;
import com.back.sanction.mapper.SanctionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PenaltyService {

    private final SanctionMapper sanctionMapper;

    private static final String DEFAULT_CAUTION_PER_DELETE = "2";
    private static final String DEFAULT_CAUTION_PER_WARNING = "10";
    private static final String DEFAULT_CAUTION_TTL_DAYS = "365";
    private static final String DEFAULT_WARNING_TTL_DAYS = "365";

    private int policyInt(String code, String defaultValue) {
        String value = sanctionMapper.findPolicySetting(code);
        return Integer.parseInt(value != null ? value : defaultValue);
    }

    // 관리자가 게시글/댓글을 직접 삭제했을 때 작성자에게 주의 포인트를 부여하고,
    // 누적 결과에 따라 경고 전환 -> 정지/영구차단까지 한 번에 처리.
    // 반환값은 생성된 moderation_log의 actionId (신고 수락 처리 시 reports_log와 연결하기 위함)
    @Transactional
    public Long applyDeletionPenalty(Long authorUserId, String targetType, Long targetId,
                                      Long reasonId, String detail, Long actedByAdminId) {
        ModerationLogDto moderationLog = new ModerationLogDto();
        moderationLog.setTargetType(targetType);
        moderationLog.setTargetId(targetId);
        moderationLog.setAppliedState("deleted");
        moderationLog.setReasonId(reasonId);
        moderationLog.setDetail(detail);
        moderationLog.setActedBy(actedByAdminId);
        sanctionMapper.insertModerationLog(moderationLog);

        int cautionPoints = policyInt("caution_per_delete", DEFAULT_CAUTION_PER_DELETE);
        int cautionPerWarning = policyInt("cautions_per_warning", DEFAULT_CAUTION_PER_WARNING);
        int cautionTtlDays = policyInt("caution_ttl_days", DEFAULT_CAUTION_TTL_DAYS);
        int warningTtlDays = policyInt("warning_ttl_days", DEFAULT_WARNING_TTL_DAYS);

        int oldSum = sanctionMapper.sumValidCautionPoints(authorUserId);

        PenaltyLogDto penaltyLog = new PenaltyLogDto();
        penaltyLog.setUserId(authorUserId);
        penaltyLog.setPoints(cautionPoints);
        penaltyLog.setTargetType(targetType);
        penaltyLog.setTargetId(targetId);
        penaltyLog.setSourceActionId(moderationLog.getActionId());
        penaltyLog.setExpiresAt(LocalDateTime.now().plusDays(cautionTtlDays));
        sanctionMapper.insertPenaltyLog(penaltyLog);

        int newSum = oldSum + cautionPoints;
        int warningsToIssue = (newSum / cautionPerWarning) - (oldSum / cautionPerWarning);

        for (int i = 0; i < warningsToIssue; i++) {
            sanctionMapper.insertWarningLog(authorUserId, LocalDateTime.now().plusDays(warningTtlDays));
            int currentWarningNo = sanctionMapper.countValidWarnings(authorUserId);

            BanPolicyDto policy = sanctionMapper.findBanPolicyByWarningNo(currentWarningNo);
            if (policy == null) {
                // 정의된 차단 정책보다 큰 경고 단계(예: 이미 영구차단된 유저)는 추가 조치 없이 무시
                log.info("경고 {}회 도달, 매칭되는 차단 정책 없음 (userId={})", currentWarningNo, authorUserId);
                continue;
            }

            LocalDateTime startsAt = LocalDateTime.now();
            LocalDateTime endsAt = "permanent".equals(policy.getBanType())
                    ? null
                    : startsAt.plusDays(policy.getBanDays());

            sanctionMapper.insertBanLog(authorUserId, policy.getWarningNo(), policy.getBanType(), startsAt, endsAt);
            sanctionMapper.updateUserBanStatus(authorUserId, policy.getBanType(), endsAt);

            log.warn("유저 {} 제재 적용 - 경고 {}회, banType={}, endsAt={}",
                    authorUserId, currentWarningNo, policy.getBanType(), endsAt);
        }

        return moderationLog.getActionId();
    }
}
