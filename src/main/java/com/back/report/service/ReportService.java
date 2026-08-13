package com.back.report.service;

import com.back.report.dto.ReportRequest;
import com.back.report.exception.ReportException;
import com.back.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

    private Long getCurrentUserId() {
        String subValue = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(subValue);
        } catch (NumberFormatException e) {
            throw new ReportException("로그인이 필요한 서비스입니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    // 게시글/댓글 신고 접수
    @Transactional
    public void submitReport(ReportRequest request) {
        Long reporterId = getCurrentUserId();

        if (!"post".equals(request.getTargetType()) && !"comment".equals(request.getTargetType())) {
            throw new ReportException("targetType은 post 또는 comment여야 합니다.", HttpStatus.BAD_REQUEST);
        }

        Long authorId = "post".equals(request.getTargetType())
                ? reportMapper.findPostAuthorId(request.getTargetId())
                : reportMapper.findCommentAuthorId(request.getTargetId());
        if (authorId == null) {
            throw new ReportException("존재하지 않는 게시글/댓글입니다.", HttpStatus.NOT_FOUND);
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
