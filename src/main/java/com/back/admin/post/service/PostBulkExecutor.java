package com.back.admin.post.service;

import com.back.admin.moderation.service.ModerationService;
import com.back.admin.report.dto.ReportStatus;
import com.back.admin.report.mapper.AdminReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_POST;

// 게시글 1건 조치를 "독립 트랜잭션"으로 실행하는 컴포넌트.
// 일괄 처리 시 한 건이 실패해도 다른 건에 영향이 없도록 REQUIRES_NEW 로 분리한다.
// (별도 빈이라 프록시를 타므로 self-invocation 문제도 없음)
@Component
@RequiredArgsConstructor
public class PostBulkExecutor {

    private final ModerationService moderationService;
    private final AdminReportMapper adminReportMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deletePost(Long postId, Long adminId, Long reasonId, String detail) {
        moderationService.softDelete(TARGET_POST, postId, adminId, reasonId, detail);
        // 삭제 시 해당 게시글의 미처리(pending) 신고도 함께 종료 (게시글 관리 ↔ 신고 관리 상태 일치).
        // 신고가 없으면 0건 갱신되어 무해.
        adminReportMapper.updatePendingReportsStatus(TARGET_POST, postId, ReportStatus.RESOLVED.dbValue());
    }
}
