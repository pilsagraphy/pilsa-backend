package com.back.admin.comment.service;

import com.back.admin.comment.dto.AdminCommentListResponse;
import com.back.admin.comment.dto.AdminCommentPageResponse;
import com.back.admin.comment.mapper.AdminCommentMapper;
import com.back.admin.common.AdminServiceSupport;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCommentServiceImpl implements AdminCommentService {

    private final AdminCommentMapper adminCommentMapper;

    @Override
    public AdminCommentPageResponse getCommentList(int page, int size, Long boardId, String keyword) {
        AuthUtils.requireAdmin(); // URL(/api/admin/**) 규칙과 별개의 서비스단 방어선
        page = AdminServiceSupport.clampPage(page);
        size = AdminServiceSupport.clampSize(size);
        keyword = AdminServiceSupport.escapeLike(keyword); // LIKE 와일드카드(%,_) 리터럴화

        int totalCount = adminCommentMapper.countComments(boardId, keyword);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        int offset = (page - 1) * size;
        List<AdminCommentListResponse> comments = adminCommentMapper.findComments(boardId, keyword, offset, size);

        AdminCommentPageResponse response = new AdminCommentPageResponse();
        response.setTotalCount(totalCount);
        response.setTotalPages(totalPages);
        response.setComments(comments);
        return response;
    }
}
