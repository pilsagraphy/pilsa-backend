package com.back.admin.post.service;

import com.back.admin.common.dto.BulkResultResponse;
import com.back.admin.post.dto.AdminPostPageResponse;

import java.util.List;

public interface AdminPostService {

    // 전체 게시글 목록 조회 (최신순, 게시판 필터, 제목+글쓴이 검색, 페이징)
    AdminPostPageResponse getPostList(int page, int size, Long boardId, String keyword);

    // 블라인드 처리 (사유 필수 모달)
    void blindPost(Long postId, Long reasonId, String detail);

    // 공개(복원)
    void restorePost(Long postId);

    // 소프트 삭제 (사유 모달 + 작성자 주의 +2)
    void deletePost(Long postId, Long reasonId, String detail);

    // 선택 삭제 (일괄, 부분 성공) — 성공 개수 + 실패 항목(id, 사유) 반환
    BulkResultResponse bulkDeletePosts(List<Long> postIds, Long reasonId, String detail);
}
