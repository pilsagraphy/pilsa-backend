package com.back.report.service;

import com.back.admin.common.AdminServiceSupport;
import com.back.admin.common.dto.BulkResultResponse;
import com.back.report.dto.ReportPageResponse;
import com.back.report.dto.ReportedItemResponse;
import com.back.report.exception.ReportAdminException;
import com.back.report.mapper.ReportAdminMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_COMMENT;
import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_POST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportAdminServiceImpl implements ReportAdminService {

    private final ReportAdminMapper reportAdminMapper;
    private final ReportBulkExecutor reportBulkExecutor;

    // targetType 검증 (post/comment 만 허용)
    private void validateTargetType(String targetType) {
        if (!TARGET_POST.equals(targetType) && !TARGET_COMMENT.equals(targetType)) {
            throw new ReportAdminException("잘못된 대상 유형입니다: " + targetType, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ReportPageResponse getReportedPosts(int page, int size, String status, Long boardId, String sort) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        page = AdminServiceSupport.clampPage(page);
        size = AdminServiceSupport.clampSize(size);
        int totalCount = reportAdminMapper.countReportedPosts(status, boardId);
        int totalPages = (int) Math.ceil((double) totalCount / size);
        int offset = (page - 1) * size;
        List<ReportedItemResponse> items = reportAdminMapper.findReportedPosts(status, boardId, sort, offset, size);
        return toPage(totalPages, totalCount, items);
    }

    @Override
    public ReportPageResponse getReportedComments(int page, int size, String status, Long boardId, String sort) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        page = AdminServiceSupport.clampPage(page);
        size = AdminServiceSupport.clampSize(size);
        int totalCount = reportAdminMapper.countReportedComments(status, boardId);
        int totalPages = (int) Math.ceil((double) totalCount / size);
        int offset = (page - 1) * size;
        List<ReportedItemResponse> items = reportAdminMapper.findReportedComments(status, boardId, sort, offset, size);
        return toPage(totalPages, totalCount, items);
    }

    @Override
    public void reject(String targetType, Long targetId) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        validateTargetType(targetType);
        reportBulkExecutor.rejectItem(targetType, targetId, AdminServiceSupport.currentAdminId());
    }

    @Override
    public void delete(String targetType, Long targetId) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        validateTargetType(targetType);
        reportBulkExecutor.deleteItem(targetType, targetId, AdminServiceSupport.currentAdminId());
    }

    @Override
    public BulkResultResponse bulkReject(String targetType, List<Long> targetIds) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        validateTargetType(targetType);
        if (CollectionUtils.isEmpty(targetIds)) {
            throw new ReportAdminException("반려할 항목을 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }
        Long adminId = AdminServiceSupport.currentAdminId();

        int successCount = 0;
        List<BulkResultResponse.FailureItem> failures = new ArrayList<>();
        for (Long targetId : new LinkedHashSet<>(targetIds)) {
            try {
                reportBulkExecutor.rejectItem(targetType, targetId, adminId);
                successCount++;
            } catch (Exception e) {
                failures.add(new BulkResultResponse.FailureItem(targetId, AdminServiceSupport.resolveFailureMessage(e)));
            }
        }
        return new BulkResultResponse(successCount, failures);
    }

    @Override
    public BulkResultResponse bulkDelete(String targetType, List<Long> targetIds) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        validateTargetType(targetType);
        if (CollectionUtils.isEmpty(targetIds)) {
            throw new ReportAdminException("삭제할 항목을 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }
        Long adminId = AdminServiceSupport.currentAdminId();

        int successCount = 0;
        List<BulkResultResponse.FailureItem> failures = new ArrayList<>();
        for (Long targetId : new LinkedHashSet<>(targetIds)) {
            try {
                reportBulkExecutor.deleteItem(targetType, targetId, adminId);
                successCount++;
            } catch (Exception e) {
                failures.add(new BulkResultResponse.FailureItem(targetId, AdminServiceSupport.resolveFailureMessage(e)));
            }
        }
        return new BulkResultResponse(successCount, failures);
    }

    private ReportPageResponse toPage(int totalPages, int totalCount, List<ReportedItemResponse> items) {
        ReportPageResponse response = new ReportPageResponse();
        response.setTotalPages(totalPages);
        response.setTotalCount(totalCount);
        response.setItems(items);
        return response;
    }
}
