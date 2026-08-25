package com.back.admin.sanction.service;

import com.back.admin.moderation.dto.ModerationState;
import com.back.admin.common.AdminServiceSupport;
import com.back.admin.sanction.dto.BulkResultResponse;
import com.back.admin.sanction.dto.ReportPageResponse;
import com.back.admin.sanction.dto.ReportedItemResponse;
import com.back.admin.sanction.exception.ReportAdminException;
import com.back.admin.sanction.mapper.ReportAdminMapper;
import com.back.global.security.AuthUtils;
import com.back.global.util.PageUtils;
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

    // 상태 필터 화이트리스트: blind/deleted 만 허용. 그 외(오타·normal·null)는 null 로 정규화 →
    // 매퍼가 기본 분기(반려된 신고만 제외, pending·blind·deleted 노출)를 타므로 잘못된 state 값이 SQL 로 새지 않는다.
    private String normalizeState(String state) {
        if (ModerationState.BLIND.dbValue().equals(state) || ModerationState.DELETED.dbValue().equals(state)) {
            return state;
        }
        return null;
    }

    @Override
    public ReportPageResponse getReportedPosts(int page, int size, String state, String keyword, Long boardId, String sort) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        page = AdminServiceSupport.clampPage(page);
        size = AdminServiceSupport.clampSize(size);
        state = normalizeState(state);                     // blind/deleted 만 허용, 그 외 기본(normal 제외)
        keyword = AdminServiceSupport.escapeLike(keyword);  // LIKE 와일드카드(%,_) 리터럴화
        int totalCount = reportAdminMapper.countReportedPosts(state, keyword, boardId);
        int totalPages = (int) Math.ceil((double) totalCount / size);
        int offset = PageUtils.offset(page, size);
        List<ReportedItemResponse> items = reportAdminMapper.findReportedPosts(state, keyword, boardId, sort, offset, size);
        return toPage(totalPages, totalCount, items);
    }

    @Override
    public ReportPageResponse getReportedComments(int page, int size, String state, String keyword, Long boardId, String sort) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        page = AdminServiceSupport.clampPage(page);
        size = AdminServiceSupport.clampSize(size);
        state = normalizeState(state);                     // blind/deleted 만 허용, 그 외 기본(normal 제외)
        keyword = AdminServiceSupport.escapeLike(keyword);  // LIKE 와일드카드(%,_) 리터럴화
        int totalCount = reportAdminMapper.countReportedComments(state, keyword, boardId);
        int totalPages = (int) Math.ceil((double) totalCount / size);
        int offset = PageUtils.offset(page, size);
        List<ReportedItemResponse> items = reportAdminMapper.findReportedComments(state, keyword, boardId, sort, offset, size);
        return toPage(totalPages, totalCount, items);
    }

    @Override
    @Transactional
    public BulkResultResponse selectRestore(String targetType, List<Long> targetIds) {
        return execute(targetType, targetIds, "복원할",
                (adminId, targetId) -> reportBulkExecutor.restoreItem(targetType, targetId, adminId));
    }

    @Override
    @Transactional
    public BulkResultResponse selectDelete(String targetType, List<Long> targetIds, Long reasonId, String detail) {
        return execute(targetType, targetIds, "삭제할",
                (adminId, targetId) -> reportBulkExecutor.deleteItem(targetType, targetId, adminId, reasonId, detail));
    }

    @Override
    @Transactional
    public BulkResultResponse selectBlind(String targetType, List<Long> targetIds, Long reasonId, String detail) {
        return execute(targetType, targetIds, "블라인드 처리할",
                (adminId, targetId) -> reportBulkExecutor.blindItem(targetType, targetId, adminId, reasonId, detail));
    }

    // 조치 1건을 실행하는 동작 (executor 호출부만 다르다)
    @FunctionalInterface
    private interface ItemAction {
        void run(Long adminId, Long targetId);
    }

    /**
     * 선택 처리 공통 루프.
     * 항목마다 독립 트랜잭션(REQUIRES_NEW)이라 한 건이 실패해도 나머지는 그대로 처리되고,
     * 실패분은 사유와 함께 응답에 담긴다(부분 성공). 중복 id는 한 번만 처리한다.
     */
    private BulkResultResponse execute(String targetType, List<Long> targetIds, String actionLabel, ItemAction action) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        validateTargetType(targetType);
        if (CollectionUtils.isEmpty(targetIds)) {
            throw new ReportAdminException(actionLabel + " 항목을 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }
        Long adminId = AdminServiceSupport.currentAdminId();

        int successCount = 0;
        List<BulkResultResponse.FailureItem> failures = new ArrayList<>();
        for (Long targetId : new LinkedHashSet<>(targetIds)) {
            try {
                action.run(adminId, targetId);
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
