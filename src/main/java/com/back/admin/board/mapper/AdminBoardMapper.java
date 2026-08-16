package com.back.admin.board.mapper;

import com.back.board.dto.BoardPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 게시판 관리(관리자) 매퍼 — 생성/수정/삭제와 관리 화면 전용 조회.
 *
 * 게시판 정책 조회(findBoardPolicy/findBoardPolicies)는 회원 요청의 권한 판정에도 쓰이는
 * 도메인 공용 쿼리라 board 의 BoardMapper 에 남겨두고 그대로 재사용한다 (쿼리 중복 방지).
 */
@Mapper
public interface AdminBoardMapper {

    /** 관리 목록의 '게시글 수' 컬럼 (삭제 글 제외) */
    int countPostsByBoard(@Param("boardId") Long boardId);

    /** 게시판명 중복 검사 (수정 시 자기 자신은 제외) */
    boolean existsBoardName(@Param("name") String name, @Param("excludeBoardId") Long excludeBoardId);

    void insertBoard(@Param("board") BoardPolicy board);

    int updateBoard(@Param("boardId") Long boardId, @Param("board") BoardPolicy board);

    /** 게시판도 소프트삭제 (글·댓글이 남아 있으므로 물리삭제하지 않는다) */
    int deleteBoard(@Param("boardId") Long boardId);

    /** 게시판 생성 시 '중요'(code=PINNED) 카테고리 자동 생성 — 없으면 새 게시판에서 상단고정을 못 쓴다 */
    void insertPinnedCategory(@Param("boardId") Long boardId);
}
