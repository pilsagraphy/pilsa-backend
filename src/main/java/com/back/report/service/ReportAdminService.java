package com.back.report.service;

import com.back.report.dto.ReportDto;
import com.back.report.dto.ReportedContentResponse;
import com.back.report.exception.ReportException;
import com.back.report.mapper.ReportMapper;
import com.back.sanction.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportAdminService {

    private final ReportMapper reportMapper;
    private final PenaltyService penaltyService;

    // 특정 회원이 작성한 게시글/댓글이 받은 신고 내역 전체
    public List<ReportedContentResponse> getReportsByTargetAuthor(Long userId) {
        return reportMapper.findReportsByTargetAuthor(userId);
    }

    // 신고 수락: 대상 소프트 삭제 + 작성자 주의 포인트 부여 + 신고 처리 결과 연결
    @Transactional
    public void resolveReport(Long reportId, Long adminUserId) {
        ReportDto report = getPendingReportOrThrow(reportId);

        Long authorId;
        if ("post".equals(report.getTargetType())) {
            authorId = reportMapper.findPostAuthorId(report.getTargetId());
            if (authorId == null) throw new ReportException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
            reportMapper.softDeletePost(report.getTargetId());
        } else {
            authorId = reportMapper.findCommentAuthorId(report.getTargetId());
            if (authorId == null) throw new ReportException("존재하지 않는 댓글입니다.", HttpStatus.NOT_FOUND);
            reportMapper.softDeleteComment(report.getTargetId());
        }

        Long actionId = penaltyService.applyDeletionPenalty(authorId, report.getTargetType(), report.getTargetId(),
                report.getReasonId(), report.getDetail(), adminUserId);

        reportMapper.resolveReport(reportId, actionId);
    }

    // 신고 거절: 삭제/패널티 없이 신고만 종료
    @Transactional
    public void rejectReport(Long reportId) {
        getPendingReportOrThrow(reportId);
        reportMapper.rejectReport(reportId);
    }

    private ReportDto getPendingReportOrThrow(Long reportId) {
        ReportDto report = reportMapper.findReportById(reportId);
        if (report == null) {
            throw new ReportException("존재하지 않는 신고입니다.", HttpStatus.NOT_FOUND);
        }
        if (!"pending".equals(report.getStatus())) {
            throw new ReportException("이미 처리된 신고입니다.", HttpStatus.CONFLICT);
        }
        return report;
    }
}
