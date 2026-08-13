package com.back.admin.post.service;

import com.back.admin.common.AdminServiceSupport;
import com.back.admin.common.dto.BulkResultResponse;
import com.back.admin.moderation.service.ModerationService;
import com.back.admin.post.dto.AdminPostDetailResponse;
import com.back.admin.post.dto.AdminPostListResponse;
import com.back.admin.post.dto.AdminPostPageResponse;
import com.back.admin.post.exception.AdminPostException;
import com.back.admin.post.mapper.AdminPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.back.admin.moderation.service.ModerationServiceImpl.TARGET_POST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private final AdminPostMapper adminPostMapper;
    private final ModerationService moderationService;
    private final PostBulkExecutor postBulkExecutor;

    @Override
    public AdminPostPageResponse getPostList(int page, int size, Long boardId, String keyword) {
        page = AdminServiceSupport.clampPage(page);
        size = AdminServiceSupport.clampSize(size);

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
    public AdminPostDetailResponse getPostDetail(Long postId) {
        // state 필터 없이 조회 → 블라인드/삭제 글도 열람. 조회수는 올리지 않음.
        AdminPostDetailResponse detail = adminPostMapper.findPostDetail(postId);
        if (detail == null) {
            throw new AdminPostException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }
        detail.setAttachments(adminPostMapper.findAttachments(postId));
        detail.setComments(adminPostMapper.findComments(postId));  // 블라인드/삭제 댓글까지 포함
        return detail;
    }

    @Override
    @Transactional
    public void blindPost(Long postId, Long reasonId, String detail) {
        moderationService.blind(TARGET_POST, postId, AdminServiceSupport.currentAdminId(), reasonId, detail);
    }

    @Override
    @Transactional
    public void restorePost(Long postId) {
        moderationService.restore(TARGET_POST, postId, AdminServiceSupport.currentAdminId());
    }

    @Override
    public void deletePost(Long postId, Long reasonId, String detail) {
        // 항목별 독립 트랜잭션으로 실행 (단건도 동일 경로 사용)
        postBulkExecutor.deletePost(postId, AdminServiceSupport.currentAdminId(), reasonId, detail);
    }

    @Override
    public BulkResultResponse bulkDeletePosts(List<Long> postIds, Long reasonId, String detail) {
        if (CollectionUtils.isEmpty(postIds)) {
            throw new AdminPostException("삭제할 게시글을 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }
        Long adminId = AdminServiceSupport.currentAdminId();

        int successCount = 0;
        List<BulkResultResponse.FailureItem> failures = new ArrayList<>();
        // 요청 내 중복 id 제거 (중복 조치로 인한 불필요한 반복 방지)
        for (Long postId : new LinkedHashSet<>(postIds)) {
            try {
                postBulkExecutor.deletePost(postId, adminId, reasonId, detail);
                successCount++;
            } catch (Exception e) {
                // 한 건 실패는 다른 건에 영향 없음 (REQUIRES_NEW). 실패 사유 수집
                failures.add(new BulkResultResponse.FailureItem(postId, AdminServiceSupport.resolveFailureMessage(e)));
            }
        }
        return new BulkResultResponse(successCount, failures);
    }
}
