package com.back.board.report.service;

import com.back.board.report.dto.ReportReasonResponse;
import com.back.board.report.dto.ReportRequest;
import com.back.board.report.exception.ReportException;
import com.back.board.report.mapper.ReportMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class ReportService {

    // reasons.code — 상세 사유(detail)를 받는 유일한 사유
    private static final String REASON_ETC = "ETC";
    // reports_log.detail 은 varchar(500) — 넘기면 제약 위반으로 500 이 난다
    private static final int DETAIL_MAX_LENGTH = 500;

    private final ReportMapper reportMapper;

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
        // 관리자 신고 목록·모달이 그 값을 신고자가 적은 상세로 표시하므로 접수 시점에 막는다.
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
        } else if (detail != null) {
            throw new ReportException("상세 내용은 '기타' 사유일 때만 입력할 수 있습니다.", HttpStatus.BAD_REQUEST);
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
