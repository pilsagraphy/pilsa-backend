package com.back.admin.board.mapper;

import com.back.admin.board.dto.AdminCategoryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 게시판별 카테고리 관리 매퍼.
 *
 * 회원 화면의 카테고리 조회(BoardMapper.findCategoriesByBoardId)는 그대로 두고, 관리 쿼리만 여기서 갖는다.
 *
 * 이 표에는 유니크가 둘 있다 — (board_id, code) 와 (board_id, name). 둘 다 is_active 를 보지 않으므로
 * 끈 카테고리가 이름을 계속 쥐고 있다. 그래서 같은 이름으로 다시 만들 때는 새로 넣지 않고
 * 꺼져 있던 줄을 되살린다(findByName + reactivate).
 */
@Mapper
public interface AdminCategoryMapper {

    /** 관리 화면 목록 — 켜져 있는 카테고리 전부(중요 포함)와 각 카테고리를 쓰는 글 수 */
    List<AdminCategoryResponse> findCategories(@Param("boardId") Long boardId);

    /** 같은 이름이 이미 쓰이고 있는가 (수정 시 자기 자신은 제외) */
    boolean existsActiveName(@Param("boardId") Long boardId,
                             @Param("name") String name,
                             @Param("excludeCategoryId") Long excludeCategoryId);

    /** 방금 넣은 줄의 id (이름은 게시판 안에서 유일하다) */
    Long findActiveIdByName(@Param("boardId") Long boardId, @Param("name") String name);

    /** 같은 이름으로 꺼져 있는 줄의 id. 유니크가 is_active 를 보지 않아 새로 넣지 못하므로 되살린다. */
    Long findInactiveIdByName(@Param("boardId") Long boardId, @Param("name") String name);

    AdminCategoryResponse findById(@Param("boardId") Long boardId, @Param("categoryId") Long categoryId);

    void insertCategory(@Param("boardId") Long boardId,
                        @Param("name") String name,
                        @Param("code") String code,
                        @Param("displayOrder") Integer displayOrder);

    /** 꺼져 있던 줄을 되살린다(이름이 같은 카테고리를 다시 만들 때) */
    int reactivateCategory(@Param("categoryId") Long categoryId, @Param("displayOrder") Integer displayOrder);

    int updateName(@Param("categoryId") Long categoryId, @Param("name") String name);

    /** 카테고리도 소프트삭제 — 글이 달고 있던 흔적을 남긴다 */
    int deactivateCategory(@Param("categoryId") Long categoryId);

    /** 이 카테고리를 달고 있는 살아있는 글 수 */
    int countPostsByCategory(@Param("categoryId") Long categoryId);

    /** 순번 재부여의 기준 — 중요(PINNED)는 항상 맨 뒤(99)라 빼고 센다 */
    List<Long> findLiveCategoryIdsOrdered(@Param("boardId") Long boardId);

    void applyDisplayOrder(@Param("categoryIds") List<Long> categoryIds);
}
