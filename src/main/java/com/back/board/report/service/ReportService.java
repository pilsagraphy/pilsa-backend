package com.back.board.report.service;

import com.back.board.report.dto.ReportReasonResponse;
import com.back.board.report.dto.ReportRequest;
import com.back.board.report.exception.ReportException;
import com.back.board.report.mapper.ReportMapper;
import com.back.admin.moderation.service.ModerationService;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 신고 접수 서비스.
 *
 * 신고는 신분(재학생/졸업생)이나 관리자 여부와 무관하게 "로그인한 회원이면 누구나" 동일하게 접수한다.
 * 관리자가 특별한 점은 신고를 거치지 않고 곧바로 조치(블라인드/삭제)할 수 있다는 것뿐이며,
 * 그 조치는 admin.moderation 이 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    // reasons.code — 상세 사유(detail)를 받는 유일한 사유
    private static final String REASON_ETC = "ETC";
    // reports_log.detail 은 varchar(500) — 넘기면 제약 위반으로 500 이 난다
    private static final int DETAIL_MAX_LENGTH = 500;

    // 자동 블라인드: 대기 신고가 이 수(신고자 수)에 닿으면 관리자 손을 거치지 않고 가린다.
    // 값은 policy_settings.auto_blind_threshold. 행이 없으면 3.
    private static final String POLICY_AUTO_BLIND = "auto_blind_threshold";
    private static final int DEFAULT_AUTO_BLIND_THRESHOLD = 3;
    // 비밀 댓글은 원글 작성자 한 사람만 볼 수 있어 신고도 한 건이 최대다 → 한 건이면 바로 가린다 (2026-09-20 PM)
    private static final int PRIVATE_COMMENT_THRESHOLD = 1;

    private final ReportMapper reportMapper;
    private final ModerationService moderationService;

    // 신고 사유 카테고리 목록 (신고 모달 셀렉트바). 로그인 회원 공통
    @Transactional(readOnly = true)
    public List<ReportReasonResponse> getReasons() {
        return reportMapper.findReasons();
    }

    // 게시글/댓글 신고 접수
    @Transactional
    public void submitReport(ReportRequest request) {
        Long reporterId = AuthUtils.currentUserId();

        if (!"post".equals(request.getTargetType()) && !"comment".equals(request.getTargetType())) {
            throw new ReportException("targetType은 post 또는 comment여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (request.getTargetId() == null || request.getReasonId() == null) {
            throw new ReportException("신고 대상과 사유는 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        // 사유 검증 + detail 정책 강제. 정본(POST /api/user/reports)이 "detail 은 기타 사유일 때만"이라
        // 규정하지만, 프론트 모달만 믿으면 API 직접 호출로 아무 사유에나 상세가 실린다 —
        // 관리자 신고 목록·모달이 그 값을 신고자가 적은 상세로 표시하므로 접수 시점에 정리한다.
        String reasonCode = reportMapper.findActiveReasonCode(request.getReasonId());
        if (reasonCode == null) {
            throw new ReportException("존재하지 않거나 사용하지 않는 신고 사유입니다.", HttpStatus.BAD_REQUEST);
        }
        String detail = normalizeDetail(request.getDetail());
        if (REASON_ETC.equals(reasonCode)) {
            if (detail == null) {
                throw new ReportException("'기타' 사유는 상세 내용을 입력해 주세요.", HttpStatus.BAD_REQUEST);
            }
            if (detail.length() > DETAIL_MAX_LENGTH) {
                throw new ReportException("상세 내용은 " + DETAIL_MAX_LENGTH + "자 이하로 입력해 주세요.", HttpStatus.BAD_REQUEST);
            }
        } else {
            // 기타가 아니면 상세는 버리고 접수한다 — 거절하면 사유를 바꿀 때 입력칸을 비우는 책임이
            // 프론트로 넘어간다. 신고 자체는 유효하므로 서버가 정리하고 통과시키는 편이 맞다.
            detail = null;
        }

        Long authorId = "post".equals(request.getTargetType())
                ? reportMapper.findPostAuthorId(request.getTargetId())
                : reportMapper.findCommentAuthorId(request.getTargetId());
        if (authorId == null) {
            throw new ReportException("존재하지 않는 게시글/댓글입니다.", HttpStatus.NOT_FOUND);
        }

        // 본인 콘텐츠는 신고 대상이 아니다
        if (authorId.equals(reporterId)) {
            throw new ReportException("본인이 작성한 게시글/댓글은 신고할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        // 이미 삭제(soft delete)된 대상은 접수하지 않는다 — 처리할 조치가 없어 관리자 큐만 오염시킨다
        String state = "post".equals(request.getTargetType())
                ? reportMapper.findPostState(request.getTargetId())
                : reportMapper.findCommentState(request.getTargetId());
        if ("deleted".equals(state)) {
            throw new ReportException("이미 삭제된 게시글/댓글입니다.", HttpStatus.CONFLICT);
        }

        try {
            reportMapper.insertReport(reporterId, request.getTargetType(), request.getTargetId(),
                    request.getReasonId(), detail);
        } catch (DuplicateKeyException e) {
            // reports_log의 uq_reports_active(reporter_id, target_type, target_id, active_flag) 유니크 제약 위반
            throw new ReportException("이미 신고한 게시글/댓글입니다.", HttpStatus.CONFLICT);
        }

        autoBlindIfNeeded(request.getTargetType(), request.getTargetId(), request.getReasonId(), state);
    }

    /**
     * 대기 신고가 기준에 닿으면 자동 블라인드. 관리자 조치와 같은 길(ModerationService)로 가려서
     * moderation_log 에 남고(acted_by 는 NULL = 자동), 신고 관리에서 복원·삭제로 이어진다.
     * 블라인드는 벌점을 붙이지 않는다 — 벌점은 관리자가 신고를 보고 삭제할 때만.
     */
    private void autoBlindIfNeeded(String targetType, Long targetId, Long reasonId, String currentState) {
        if (!"normal".equals(currentState)) return; // 이미 가려져 있으면 할 일이 없다

        int threshold = autoBlindThreshold(targetType, targetId);
        int reporters = reportMapper.countPendingReporters(targetType, targetId);
        if (reporters < threshold) return;

        moderationService.blind(targetType, targetId, null, reasonId,
                "신고 " + reporters + "건 누적으로 자동 블라인드 (기준 " + threshold + "건)");
        log.info("[신고] 자동 블라인드 - {} {} (신고자 {}명, 기준 {}건)", targetType, targetId, reporters, threshold);
    }

    private int autoBlindThreshold(String targetType, Long targetId) {
        if ("comment".equals(targetType) && Boolean.TRUE.equals(reportMapper.isPrivateComment(targetId))) {
            return PRIVATE_COMMENT_THRESHOLD;
        }
        String value = reportMapper.findPolicySetting(POLICY_AUTO_BLIND);
        try {
            return value == null ? DEFAULT_AUTO_BLIND_THRESHOLD : Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_AUTO_BLIND_THRESHOLD;
        }
    }

    // 공백만 들어온 detail 은 미입력으로 본다 — 사유를 바꾼 뒤 입력창을 비우지 않고 보내는 경우를 400 으로 만들지 않기 위해
    private String normalizeDetail(String detail) {
        if (detail == null) {
            return null;
        }
        String trimmed = detail.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
