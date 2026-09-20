package com.back.admin.post.service;

import com.back.admin.common.AdminServiceSupport;
import com.back.admin.post.dto.AdminPostDetailResponse;
import com.back.admin.post.dto.AdminCommentResponse;
import com.back.admin.post.dto.ModerationNoteResponse;
import com.back.admin.post.dto.AdminPostListResponse;
import com.back.admin.post.dto.AdminPostPageResponse;
import com.back.admin.post.exception.AdminPostException;
import com.back.admin.post.mapper.AdminPostMapper;
import com.back.global.security.AuthUtils;
import com.back.global.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private final AdminPostMapper adminPostMapper;

    @Override
    public AdminPostPageResponse getPostList(int page, int size, Long boardId, String keyword) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        page = AdminServiceSupport.clampPage(page);
        size = AdminServiceSupport.clampSize(size);

        int totalCount = adminPostMapper.countPosts(boardId, keyword);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        int offset = PageUtils.offset(page, size);
        List<AdminPostListResponse> posts = adminPostMapper.findPosts(boardId, keyword, offset, size);

        AdminPostPageResponse response = new AdminPostPageResponse();
        response.setTotalCount(totalCount);
        response.setTotalPages(totalPages);
        response.setPosts(posts);
        return response;
    }

    @Override
    public AdminPostDetailResponse getPostDetail(Long postId) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        // state 필터 없이 조회 → 블라인드/삭제 글도 열람. 조회수는 올리지 않음.
        AdminPostDetailResponse detail = adminPostMapper.findPostDetail(postId);
        if (detail == null) {
            throw new AdminPostException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }
        detail.setAttachments(adminPostMapper.findAttachments(postId));
        detail.setComments(adminPostMapper.findComments(postId));  // 블라인드/삭제 댓글까지 포함

        // 왜 이 상태인지 — 글과 댓글마다 마지막 조치를 붙인다 (관리자 조치 / 신고 누적 자동 / 없으면 작성자 삭제)
        detail.setModeration(adminPostMapper.findLatestModerationForPost(postId));
        java.util.Map<Long, ModerationNoteResponse> byComment = new java.util.HashMap<>();
        for (ModerationNoteResponse note : adminPostMapper.findLatestModerationsForComments(postId)) {
            byComment.put(note.getTargetId(), note);
        }
        for (AdminCommentResponse comment : detail.getComments()) {
            comment.setModeration(byComment.get(comment.getCommentId()));
        }
        return detail;
    }
}
