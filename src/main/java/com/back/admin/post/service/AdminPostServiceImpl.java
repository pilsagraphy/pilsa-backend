package com.back.admin.post.service;

import com.back.admin.common.dto.BulkResultResponse;
import com.back.admin.moderation.service.ModerationService;
import com.back.admin.post.dto.AdminPostListResponse;
import com.back.admin.post.dto.AdminPostPageResponse;
import com.back.admin.post.exception.AdminPostException;
import com.back.admin.post.mapper.AdminPostMapper;
import com.back.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_POST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private final AdminPostMapper adminPostMapper;
    private final ModerationService moderationService;
    private final PostBulkExecutor postBulkExecutor;

    // 현재 로그인한 관리자 user_id 추출 (/api/admin/** 는 SecurityConfig 에서 ADMIN 으로 이미 제한됨)
    private Long getCurrentAdminId() {
        String subValue = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(subValue);
        } catch (NumberFormatException e) {
            throw new AdminPostException("로그인이 필요한 서비스입니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public AdminPostPageResponse getPostList(int page, int size, Long boardId, String keyword) {
        int totalCount = adminPostMapper.countPosts(boardId, keyword);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        int offset = (page - 1) * size;
        List<AdminPostListResponse> posts = adminPostMapper.findPosts(boardId, keyword, offset, size);

        AdminPostPageResponse response = new AdminPostPageResponse();
        response.setTotalCount(totalCount);
        response.setTotalPages(totalPages);
        response.setPosts(posts);
        return response;
    }

    @Override
    @Transactional
    public void blindPost(Long postId, Long reasonId, String detail) {
        Long adminId = getCurrentAdminId();
        moderationService.blind(TARGET_POST, postId, adminId, reasonId, detail);
    }

    @Override
    @Transactional
    public void restorePost(Long postId) {
        Long adminId = getCurrentAdminId();
        moderationService.restore(TARGET_POST, postId, adminId);
    }

    @Override
    public void deletePost(Long postId, Long reasonId, String detail) {
        Long adminId = getCurrentAdminId();
        // 항목별 독립 트랜잭션으로 실행 (단건도 동일 경로 사용)
        postBulkExecutor.deletePost(postId, adminId, reasonId, detail);
    }

    @Override
    public BulkResultResponse bulkDeletePosts(List<Long> postIds, Long reasonId, String detail) {
        if (CollectionUtils.isEmpty(postIds)) {
            throw new AdminPostException("삭제할 게시글을 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }
        Long adminId = getCurrentAdminId();

        int successCount = 0;
        List<BulkResultResponse.FailureItem> failures = new ArrayList<>();
        for (Long postId : postIds) {
            try {
                postBulkExecutor.deletePost(postId, adminId, reasonId, detail);
                successCount++;
            } catch (Exception e) {
                // 한 건 실패는 다른 건에 영향 없음 (REQUIRES_NEW). 실패 사유 수집
                failures.add(new BulkResultResponse.FailureItem(postId, resolveMessage(e)));
            }
        }
        return new BulkResultResponse(successCount, failures);
    }

    // 실패 사유 메시지 추출 (도메인 예외는 그대로, 그 외는 일반 메시지)
    private String resolveMessage(Exception e) {
        return (e instanceof BaseException) ? e.getMessage() : "처리 중 오류가 발생했습니다.";
    }
}
