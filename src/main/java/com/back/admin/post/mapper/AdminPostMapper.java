package com.back.admin.post.mapper;

import com.back.admin.post.dto.AdminPostListResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminPostMapper {

    // 전체 게시글 목록 (모든 게시판 통합, 최신순, 게시판 필터, 제목+글쓴이 검색, 페이징)
    // deleted 상태는 제외한다.
    List<AdminPostListResponse> findPosts(
            @Param("boardId") Long boardId,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    // 위 조건에 해당하는 총 개수 (페이징 계산용)
    int countPosts(
            @Param("boardId") Long boardId,
            @Param("keyword") String keyword
    );
}
