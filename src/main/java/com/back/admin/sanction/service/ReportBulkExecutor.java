package com.back.admin.sanction.service;

import com.back.admin.moderation.dto.ModerationState;
import com.back.admin.moderation.service.ModerationService;
import com.back.admin.sanction.dto.ReportStatus;
import com.back.admin.sanction.mapper.ReportAdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 신고 대상 1건 조치(복원/삭제/블라인드)를 "독립 트랜잭션"으로 실행하는 컴포넌트.
// 조치는 (상태 변경 + moderation_log + reports_log 갱신)이 항목 단위로 원자적이어야 하므로
// REQUIRES_NEW 로 묶는다. 일괄 처리 중 한 건이 실패해도 나머지에 영향이 없다.
@Component
@RequiredArgsConstructor
public class ReportBulkExecutor {

    private final ModerationService moderationService;
    private final ReportAdminMapper reportAdminMapper;

    // 복원(=신고 반려): 블라인드 → 공개 복원 + 신고 rejected
    // 단, 이미 삭제(deleted)된 대상은 되살리지 않는다 (의도적으로 삭제된 콘텐츠·벌점 보호)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreItem(String targetType, Long targetId, Long adminId) {
        if (!ModerationState.DELETED.dbValue().equals(moderationService.currentState(targetType, targetId))) {
            moderationService.restore(targetType, targetId, adminId);
        }
        // 복원은 삭제 조치가 아니므로 resolution_action_id 없이 종료
        reportAdminMapper.updatePendingReportsStatus(targetType, targetId, ReportStatus.REJECTED.dbValue(), null);
    }

    // 삭제: 소프트 삭제(주의 +2) + 신고 resolved.
    // 사유는 요청값을 우선 쓰고, 없으면 대표(최신) 신고 사유로 채운다
    // (게시글 관리 화면처럼 신고가 없는 대상도 이 API로 삭제하기 때문)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteItem(String targetType, Long targetId, Long adminId, Long reasonId, String detail) {
        Long resolvedReasonId = reasonId != null ? reasonId : reportAdminMapper.findLatestReasonId(targetType, targetId);
        // actionId: 이번 호출로 실제 삭제됐으면 그 조치 id, 이미 삭제 상태였다면 null (신고만 종료)
        Long actionId = moderationService.softDelete(targetType, targetId, adminId, resolvedReasonId, detail);
        reportAdminMapper.updatePendingReportsStatus(targetType, targetId, ReportStatus.RESOLVED.dbValue(), actionId);
    }

    // 블라인드: state=blind + 조치이력. 벌점은 부과하지 않는다(삭제와의 차이).
    // 신고는 아직 처리 중(pending)으로 남긴다 — 블라인드는 최종 판단 전 임시 조치이기 때문.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void blindItem(String targetType, Long targetId, Long adminId, Long reasonId, String detail) {
        Long resolvedReasonId = reasonId != null ? reasonId : reportAdminMapper.findLatestReasonId(targetType, targetId);
        moderationService.blind(targetType, targetId, adminId, resolvedReasonId, detail);
    }
}
