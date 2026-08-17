package com.back.board.report.service;

import com.back.board.report.dto.ReportRequest;
import com.back.board.report.exception.ReportException;
import com.back.board.report.mapper.ReportMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ReportMapper reportMapper;

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
                    request.getReasonId(), request.getDetail());
        } catch (DuplicateKeyException e) {
            // reports_log의 uq_reports_active(reporter_id, target_type, target_id, active_flag) 유니크 제약 위반
            throw new ReportException("이미 신고한 게시글/댓글입니다.", HttpStatus.CONFLICT);
        }
    }
}
