package com.back.admin.sanction.service;

import com.back.admin.moderation.service.ModerationService;
import com.back.admin.sanction.dto.ReportStatus;
import com.back.admin.sanction.exception.ReportAdminException;
import com.back.admin.sanction.mapper.ReportAdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_POST;

// 신고 대상 1건 조치(복원/삭제/블라인드)를 "독립 트랜잭션"으로 실행하는 컴포넌트.
// 조치는 (상태 변경 + moderation_log + reports_log 갱신)이 항목 단위로 원자적이어야 하므로
// REQUIRES_NEW 로 묶는다. 일괄 처리 중 한 건이 실패해도 나머지에 영향이 없다.
@Component
@RequiredArgsConstructor
public class ReportBulkExecutor {

    private final ModerationService moderationService;
    private final ReportAdminMapper reportAdminMapper;

    // 복원(=신고 반려): 블라인드/삭제 → 공개 복원 + 부과됐던 벌점 회수 + pending 신고 rejected.
    // 삭제된 대상도 되살린다(복원 = 모든 조치의 되돌리기). restore 내부에서 deleted→normal 전환과 함께 주의 포인트를 void 처리한다.
    // 단, 이미 처리(resolved/rejected)된 신고 상태는 처리 이력이라 되살리지 않고 유지한다(pending 만 rejected 로 종료).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreItem(String targetType, Long targetId, Long adminId) {
        // 존재하지 않는 대상이면 restore 내부(changeState)에서 NOT_FOUND 예외. 이미 공개(normal)면 null 반환(상태 변화 없음).
        boolean stateRestored = moderationService.restore(targetType, targetId, adminId) != null;
        int rejected = reportAdminMapper.updatePendingReportsStatus(
                targetType, targetId, ReportStatus.REJECTED.dbValue(), null);

        // 상태도 안 바뀌고(이미 공개) 반려 처리한 신고도 없으면 no-op → 실패 사유로 관리자에게 알린다.
        if (!stateRestored && rejected == 0) {
            throw new ReportAdminException("복원할 신고가 없습니다. 이미 공개 상태입니다.", HttpStatus.CONFLICT);
        }
    }

    // 삭제: 소프트 삭제(주의 +2) + 신고 resolved.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteItem(String targetType, Long targetId, Long adminId, Long reasonId, String detail) {
        // actionId: 이번 호출로 실제 삭제됐으면 그 조치 id, 이미 삭제 상태였다면 null (softDelete 의 state<>deleted 가드 — 벌점 중복 방지)
        Long actionId = moderationService.softDelete(targetType, targetId, adminId, resolveReasonId(targetType, targetId, reasonId), detail);
        // 신고 종료를 먼저 한다 — 작성자가 먼저 지운 글(state 이미 deleted, actionId=null)도 pending 신고는 정상 종료돼야 한다.
        int resolved = reportAdminMapper.updatePendingReportsStatus(
                targetType, targetId, ReportStatus.RESOLVED.dbValue(), actionId);

        // 상태 변화도 없고(이미 삭제됨) 종료한 신고도 없으면 진짜 no-op → 실패 사유로 관리자에게 알린다(부분 성공 응답의 failures 로 노출).
        // (같은 글 두 번 삭제: 첫 삭제에서 신고가 이미 종료돼 두 번째는 resolved=0 → 여기서 실패. 벌점 중복은 softDelete 가드가 막는다.)
        if (actionId == null && resolved == 0) {
            throw new ReportAdminException(
                    TARGET_POST.equals(targetType) ? "이미 삭제된 게시글입니다." : "이미 삭제된 댓글입니다.",
                    HttpStatus.CONFLICT);
        }
    }

    // 블라인드: state=blind + 조치이력. 벌점은 부과하지 않는다(삭제와의 차이).
    // 신고는 아직 처리 중(pending)으로 남긴다 — 블라인드는 최종 판단 전 임시 조치이기 때문.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void blindItem(String targetType, Long targetId, Long adminId, Long reasonId, String detail) {
        moderationService.blind(targetType, targetId, adminId, resolveReasonId(targetType, targetId, reasonId), detail);
    }

    // 조치 사유 결정: 관리자 입력값을 우선 쓰고, 없으면 대표(최신) 신고 사유로 채운다.
    // (게시글/댓글 관리 화면처럼 신고가 없는 대상도 이 API로 조치하기 때문 — 그 경우 null 가능)
    private Long resolveReasonId(String targetType, Long targetId, Long reasonId) {
        return reasonId != null ? reasonId : reportAdminMapper.findLatestReasonId(targetType, targetId);
    }
}
