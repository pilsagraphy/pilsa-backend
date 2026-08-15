package com.back.admin.sanction.service;

import com.back.admin.sanction.dto.ReportedCommentResponse;
import com.back.admin.sanction.dto.ReportedPostResponse;
import com.back.admin.sanction.dto.SanctionedUserDetailResponse;
import com.back.admin.sanction.dto.SanctionedUserResponse;
import com.back.admin.sanction.exception.SanctionException;
import com.back.admin.sanction.mapper.SanctionMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SanctionAdminService {

    private final SanctionMapper sanctionMapper;

    private static final String DEFAULT_CAUTION_PER_WARNING = "10";

    // 현재 제재(정지/영구차단/주의) 중인 회원 목록
    public List<SanctionedUserResponse> getSanctionedUsers() {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        return sanctionMapper.findSanctionedUsers();
    }

    // 특정 회원의 현재 제재 현황 (태그, 정지 기간, 누적주의, 누적경고, 신고삭제처리건수)
    public SanctionedUserDetailResponse getSanctionedUserDetail(Long userId) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        SanctionedUserResponse base = sanctionMapper.findSanctionedUserById(userId);
        if (base == null) {
            throw new SanctionException("존재하지 않는 회원입니다.", HttpStatus.NOT_FOUND);
        }

        String cautionPerWarningSetting = sanctionMapper.findPolicySetting("cautions_per_warning");
        int cautionPerWarning = Integer.parseInt(
                cautionPerWarningSetting != null ? cautionPerWarningSetting : DEFAULT_CAUTION_PER_WARNING);
        int cautionSum = sanctionMapper.sumValidCautionPoints(userId);

        SanctionedUserDetailResponse detail = new SanctionedUserDetailResponse();
        detail.setTag(base.getTag());
        detail.setBanStatus(base.getBanStatus());
        detail.setBannedUntil(base.getBannedUntil());
        detail.setBanStartedAt(base.getBanStartedAt());
        detail.setCautionRemainder(cautionSum % cautionPerWarning);
        detail.setWarningCount(sanctionMapper.countValidWarnings(userId));
        detail.setReportDeletedCount(sanctionMapper.countResolvedDeletionsByUser(userId));
        return detail;
    }

    // 특정 회원이 작성한 '게시글'이 받은 신고 내역 (제재회원 관리 화면)
    public List<ReportedPostResponse> getReportedPosts(Long userId) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        return sanctionMapper.findReportedPostsByAuthor(userId);
    }

    // 특정 회원이 작성한 '댓글'이 받은 신고 내역 (제재회원 관리 화면)
    public List<ReportedCommentResponse> getReportedComments(Long userId) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        return sanctionMapper.findReportedCommentsByAuthor(userId);
    }

    // 관리자 수동 해제
    @Transactional
    public void liftBan(Long userId, Long adminUserId) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        sanctionMapper.updateUserBanStatus(userId, "none", null);
        sanctionMapper.closeActiveBanLog(userId, adminUserId);
    }
}
