package com.back.admin.post.service;

import com.back.admin.post.support.PostAdminSupport;
import com.back.admin.post.dto.AdminPostDetailResponse;
import com.back.admin.post.dto.AdminPostListResponse;
import com.back.admin.post.dto.AdminPostPageResponse;
import com.back.admin.post.exception.AdminPostException;
import com.back.admin.post.mapper.AdminPostMapper;
import com.back.global.security.AuthUtils;
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
        page = PostAdminSupport.clampPage(page);
        size = PostAdminSupport.clampSize(size);

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
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        // state 필터 없이 조회 → 블라인드/삭제 글도 열람. 조회수는 올리지 않음.
        AdminPostDetailResponse detail = adminPostMapper.findPostDetail(postId);
        if (detail == null) {
            throw new AdminPostException("존재하지 않는 게시글입니다.", HttpStatus.NOT_FOUND);
        }
        detail.setAttachments(adminPostMapper.findAttachments(postId));
        detail.setComments(adminPostMapper.findComments(postId));  // 블라인드/삭제 댓글까지 포함
        return detail;
    }
}
