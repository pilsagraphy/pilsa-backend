package com.back.admin.report.service;

import com.back.admin.moderation.dto.ModerationState;
import com.back.admin.moderation.service.ModerationService;
import com.back.admin.report.dto.ReportStatus;
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

    private final ModerationService moderationService;
    private final AdminReportMapper adminReportMapper;

    // 반려: 블라인드 → 공개 복원 + 신고 rejected
    // 단, 이미 삭제(deleted)된 대상은 되살리지 않는다 (게시글 관리에서 의도적으로 삭제된 콘텐츠·벌점 보호)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejectItem(String targetType, Long targetId, Long adminId) {
        if (!ModerationState.DELETED.dbValue().equals(moderationService.currentState(targetType, targetId))) {
            moderationService.restore(targetType, targetId, adminId);
        }
        // 반려는 삭제 조치가 아니므로 resolution_action_id 없이 종료
        adminReportMapper.updatePendingReportsStatus(targetType, targetId, ReportStatus.REJECTED.dbValue(), null);
    }

    // 삭제: 소프트 삭제(주의 +2) + 신고 resolved. 사유는 대표(최신) 신고 사유 사용
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteItem(String targetType, Long targetId, Long adminId) {
        Long reasonId = adminReportMapper.findLatestReasonId(targetType, targetId);
        // actionId: 이번 호출로 실제 삭제됐으면 그 조치 id, 이미 삭제 상태였다면 null (신고만 종료)
        Long actionId = moderationService.softDelete(targetType, targetId, adminId, reasonId, null);
        adminReportMapper.updatePendingReportsStatus(targetType, targetId, ReportStatus.RESOLVED.dbValue(), actionId);
    }
}
