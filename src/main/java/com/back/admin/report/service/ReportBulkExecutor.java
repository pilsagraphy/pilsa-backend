package com.back.admin.report.service;

import com.back.admin.moderation.service.ModerationService;
import com.back.admin.report.mapper.AdminReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 신고 1건 조치(반려/삭제)를 "독립 트랜잭션"으로 실행하는 컴포넌트.
// 반려/삭제는 (상태 변경 + moderation_log + reports_log 갱신)이 항목 단위로 원자적이어야 하므로
// REQUIRES_NEW 로 묶는다. 단건/일괄이 모두 이 메서드를 재사용한다.
@Component
@RequiredArgsConstructor
public class ReportBulkExecutor {

    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_RESOLVED = "resolved";

    private final ModerationService moderationService;
    private final AdminReportMapper adminReportMapper;

    // 반려: 블라인드 → 공개 복원 + 신고 rejected
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejectItem(String targetType, Long targetId, Long adminId) {
        moderationService.restore(targetType, targetId, adminId);
        adminReportMapper.updatePendingReportsStatus(targetType, targetId, STATUS_REJECTED);
    }

    // 삭제: 소프트 삭제(주의 +2) + 신고 resolved. 사유는 대표(최신) 신고 사유 사용
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteItem(String targetType, Long targetId, Long adminId) {
        Long reasonId = adminReportMapper.findLatestReasonId(targetType, targetId);
        moderationService.softDelete(targetType, targetId, adminId, reasonId, null);
        adminReportMapper.updatePendingReportsStatus(targetType, targetId, STATUS_RESOLVED);
    }
}
