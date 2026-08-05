package com.back.admin.report.service;

import com.back.admin.common.dto.BulkResultResponse;
import com.back.admin.report.dto.ReportPageResponse;
import com.back.admin.report.dto.ReportedItemResponse;
import com.back.admin.report.exception.AdminReportException;
import com.back.admin.report.mapper.AdminReportMapper;
import com.back.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_COMMENT;
import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_POST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportServiceImpl implements AdminReportService {

    private final AdminReportMapper adminReportMapper;
    private final ReportBulkExecutor reportBulkExecutor;

    private Long getCurrentAdminId() {
        String subValue = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(subValue);
        } catch (NumberFormatException e) {
            throw new AdminReportException("로그인이 필요한 서비스입니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    // targetType 검증 (post/comment 만 허용)
    private void validateTargetType(String targetType) {
        if (!TARGET_POST.equals(targetType) && !TARGET_COMMENT.equals(targetType)) {
            throw new AdminReportException("잘못된 대상 유형입니다: " + targetType, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ReportPageResponse getReportedPosts(int page, int size, String status, Long boardId, String sort) {
        int totalCount = adminReportMapper.countReportedPosts(status, boardId);
        int totalPages = (int) Math.ceil((double) totalCount / size);
        int offset = (page - 1) * size;
        List<ReportedItemResponse> items = adminReportMapper.findReportedPosts(status, boardId, sort, offset, size);
        return toPage(totalPages, totalCount, items);
    }

    @Override
    public ReportPageResponse getReportedComments(int page, int size, String status, Long boardId, String sort) {
        int totalCount = adminReportMapper.countReportedComments(status, boardId);
        int totalPages = (int) Math.ceil((double) totalCount / size);
        int offset = (page - 1) * size;
        List<ReportedItemResponse> items = adminReportMapper.findReportedComments(status, boardId, sort, offset, size);
        return toPage(totalPages, totalCount, items);
    }

    @Override
    public void reject(String targetType, Long targetId) {
        validateTargetType(targetType);
        Long adminId = getCurrentAdminId();
        reportBulkExecutor.rejectItem(targetType, targetId, adminId);
    }

    @Override
    public void delete(String targetType, Long targetId) {
        validateTargetType(targetType);
        Long adminId = getCurrentAdminId();
        reportBulkExecutor.deleteItem(targetType, targetId, adminId);
    }

    @Override
    public BulkResultResponse bulkReject(String targetType, List<Long> targetIds) {
        validateTargetType(targetType);
        if (CollectionUtils.isEmpty(targetIds)) {
            throw new AdminReportException("반려할 항목을 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }
        Long adminId = getCurrentAdminId();

        int successCount = 0;
        List<BulkResultResponse.FailureItem> failures = new ArrayList<>();
        for (Long targetId : targetIds) {
            try {
                reportBulkExecutor.rejectItem(targetType, targetId, adminId);
                successCount++;
            } catch (Exception e) {
                failures.add(new BulkResultResponse.FailureItem(targetId, resolveMessage(e)));
            }
        }
        return new BulkResultResponse(successCount, failures);
    }

    @Override
    public BulkResultResponse bulkDelete(String targetType, List<Long> targetIds) {
        validateTargetType(targetType);
        if (CollectionUtils.isEmpty(targetIds)) {
            throw new AdminReportException("삭제할 항목을 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }
        Long adminId = getCurrentAdminId();

        int successCount = 0;
        List<BulkResultResponse.FailureItem> failures = new ArrayList<>();
        for (Long targetId : targetIds) {
            try {
                reportBulkExecutor.deleteItem(targetType, targetId, adminId);
                successCount++;
            } catch (Exception e) {
                failures.add(new BulkResultResponse.FailureItem(targetId, resolveMessage(e)));
            }
        }
        return new BulkResultResponse(successCount, failures);
    }

    // 실패 사유 메시지 추출 (도메인 예외는 그대로, 그 외는 일반 메시지)
    private String resolveMessage(Exception e) {
        return (e instanceof BaseException) ? e.getMessage() : "처리 중 오류가 발생했습니다.";
    }

    private ReportPageResponse toPage(int totalPages, int totalCount, List<ReportedItemResponse> items) {
        ReportPageResponse response = new ReportPageResponse();
        response.setTotalPages(totalPages);
        response.setTotalCount(totalCount);
        response.setItems(items);
        return response;
    }
}
