package com.back.admin.sanction.service;

import com.back.admin.moderation.service.ModerationService;
import com.back.admin.sanction.dto.ReportStatus;
import com.back.admin.sanction.exception.CommentRestoreBlockedException;
import com.back.admin.sanction.exception.ReportAdminException;
import com.back.admin.sanction.mapper.ReportAdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_COMMENT;
import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_POST;

// 신고 대상 1건 조치(복원/삭제/블라인드)를 "독립 트랜잭션"으로 실행하는 컴포넌트.
// 조치는 (상태 변경 + moderation_log + reports_log 갱신)이 항목 단위로 원자적이어야 하므로
// REQUIRES_NEW 로 묶는다. 일괄 처리 중 한 건이 실패해도 나머지에 영향이 없다.
//
// 관리자가 신고 없이 직접 조치한 건(게시글·댓글 관리 화면)도 신고 관리에 나타나야 한다 — 블라인드 뒤 복원·삭제를
// 밟을 곳이 신고 관리뿐이고, 삭제도 거기서 되돌린다. 그래서 신고가 하나도 없는 대상을 직접 조치하면
// reports_log 에 source='admin' 인 행을 하나 남긴다(신고자 = 조치한 관리자, 사유 = 모달에서 고른 사유).
//   블라인드 → pending (신고 관리에서 최종 판단 대기)   삭제 → resolved + resolution_action_id (처리 완료, 복원 가능)
// 이미 회원 신고가 걸려 있으면 그 신고가 목록에 있으므로 행을 더 만들지 않는다 (PM 결정, 2026-09-27).
@Slf4j
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
        // 댓글은 원 게시글이 공개 상태여야 복원할 수 있다 — 삭제·블라인드된 글 아래 댓글만 살려 봐야 아무도 못 본다.
        // 원글 상태와 누가 지웠는지(작성자 / 관리자)를 함께 알려 관리자가 게시글부터 복원하게 한다.
        if (TARGET_COMMENT.equals(targetType)) {
            requirePostVisibleForComment(targetId);
        }

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
        Long effectiveReasonId = resolveReasonId(targetType, targetId, reasonId);
        // actionId: 이번 조치로 남은 moderation_log id. 관리자가 이미 삭제 조치한 대상이면 null (softDelete 가 no-op).
        // 작성자가 먼저 지운 글은 상태가 이미 deleted 여도 조치로 인정되어 actionId 가 나온다(벌점도 부과된다).
        Long actionId = moderationService.softDelete(targetType, targetId, adminId, effectiveReasonId, detail);
        int resolved = reportAdminMapper.updatePendingReportsStatus(
                targetType, targetId, ReportStatus.RESOLVED.dbValue(), actionId);

        // 조치도 없고(이미 관리자가 삭제함) 종료한 신고도 없으면 진짜 no-op → 실패 사유로 관리자에게 알린다(failures 로 노출).
        // (같은 글 두 번 삭제: 첫 삭제에서 신고가 이미 종료돼 두 번째는 resolved=0 → 여기서 실패. 벌점 중복은 softDelete 가 막는다.)
        if (actionId == null && resolved == 0) {
            throw new ReportAdminException(
                    TARGET_POST.equals(targetType) ? "이미 삭제된 게시글입니다." : "이미 삭제된 댓글입니다.",
                    HttpStatus.CONFLICT);
        }

        // 신고 없이 바로 삭제한 건 — 신고 관리에 '처리 완료' 행으로 남겨 복원할 수 있게 한다
        if (actionId != null && resolved == 0 && !reportAdminMapper.existsAnyReport(targetType, targetId)) {
            recordAdminActionReport(targetType, targetId, adminId, effectiveReasonId, detail,
                    ReportStatus.RESOLVED.dbValue(), actionId);
        }
    }

    // 블라인드: state=blind + 조치이력. 벌점은 부과하지 않는다(삭제와의 차이).
    // 신고는 아직 처리 중(pending)으로 남긴다 — 블라인드는 최종 판단 전 임시 조치이기 때문.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void blindItem(String targetType, Long targetId, Long adminId, Long reasonId, String detail) {
        Long effectiveReasonId = resolveReasonId(targetType, targetId, reasonId);
        Long actionId = moderationService.blind(targetType, targetId, adminId, effectiveReasonId, detail);

        // 신고 없이 바로 블라인드한 건 — 신고 관리에 '처리 대기' 행으로 올려 거기서 복원·삭제를 판단하게 한다
        if (actionId != null && reportAdminMapper.countPendingReports(targetType, targetId) == 0) {
            recordAdminActionReport(targetType, targetId, adminId, effectiveReasonId, detail,
                    ReportStatus.PENDING.dbValue(), actionId);
        }
    }

    // 관리자 직접 조치를 신고 행으로 기록. 사유가 없으면(모달에서 안 고름·기존 신고도 없음) 신고 행을 만들 수 없어 건너뛴다 —
    // reports_log.reason_id 는 필수이고, 사유 없는 신고 행은 목록에서 의미가 없다.
    private void recordAdminActionReport(String targetType, Long targetId, Long adminId, Long reasonId,
                                         String detail, String status, Long actionId) {
        if (reasonId == null) {
            log.warn("관리자 직접 조치를 신고 관리에 올리지 못함 - 사유 없음. {} {} action={}", targetType, targetId, actionId);
            return;
        }
        reportAdminMapper.insertAdminActionReport(adminId, targetType, targetId, reasonId, detail, status, actionId);
    }

    // 댓글 복원 전 원 게시글 점검. 원글이 공개가 아니면 누가 지웠는지(작성자 / 관리자)와 함께 막는다.
    private void requirePostVisibleForComment(Long commentId) {
        Long postId = reportAdminMapper.findCommentPostId(commentId);
        if (postId == null) {
            throw new ReportAdminException("댓글의 원 게시글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        String postState = moderationService.currentState(TARGET_POST, postId);
        if ("normal".equals(postState)) {
            return;
        }
        // 관리자 조치 이력(블라인드·삭제, 조치자 있음)이 마지막이면 관리자 조치, 아니면 작성자 자진 삭제
        Boolean byAdmin = reportAdminMapper.isLatestPostActionByAdmin(postId);
        String deletedBy = Boolean.TRUE.equals(byAdmin) ? "admin" : "author";
        Long authorId = reportAdminMapper.findPostAuthorId(postId);
        String authorName = reportAdminMapper.findUserName(authorId);
        throw new CommentRestoreBlockedException(postId, postState, deletedBy, authorId, authorName);
    }

    // 조치 사유 결정: 관리자 입력값을 우선 쓰고, 없으면 대표(최신) 신고 사유로 채운다.
    // (게시글/댓글 관리 화면처럼 신고가 없는 대상도 이 API로 조치하기 때문 — 그 경우 null 가능)
    private Long resolveReasonId(String targetType, Long targetId, Long reasonId) {
        return reasonId != null ? reasonId : reportAdminMapper.findLatestReasonId(targetType, targetId);
    }
}
