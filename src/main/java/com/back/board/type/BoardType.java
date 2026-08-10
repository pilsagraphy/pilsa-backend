package com.back.board.type;

import com.back.board.exception.BoardException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 게시판 종류별 정책을 한 곳에 모아둔 enum.
 * boardId(DB posts.board_id)로 게시판을 구분하며, 게시판마다 다른 동작(권한/카테고리 기본값/업로드 경로)을 여기서 정의한다.
 *
 *  - NOTICE(1) : 공지사항  - 관리자만 작성/삭제, is_pinned(중요표시) 사용, 카테고리 없음
 *  - FREE(2)   : 자유게시판 - 로그인 유저 작성, 익명(is_anonymous) 사용, 카테고리 기본값 1
 *  - INFO(3)   : 정보게시판 - 로그인 유저 작성, 비밀댓글(is_private) 사용, 카테고리 기본값 2
 */
@Getter
public enum BoardType {

    NOTICE(1L, true, null, "uploads/notices"),
    FREE(2L, false, 1L, "uploads/free"),
    INFO(3L, false, 2L, "uploads/info");

    private final Long boardId;
    private final boolean adminWrite;      // true면 작성/삭제에 관리자(ROLE_ADMIN) 권한 필요 (공지사항)
    private final Long defaultCategoryId;  // 카테고리 미선택 시 기본값 (null이면 카테고리 미사용)
    private final String uploadDir;        // 첨부파일 저장 경로

    BoardType(Long boardId, boolean adminWrite, Long defaultCategoryId, String uploadDir) {
        this.boardId = boardId;
        this.adminWrite = adminWrite;
        this.defaultCategoryId = defaultCategoryId;
        this.uploadDir = uploadDir;
    }

    // boardId로 게시판 종류를 찾는다. 정의되지 않은 값이면 예외 발생(존재하지 않는 게시판)
    public static BoardType of(Long boardId) {
        if (boardId != null) {
            for (BoardType type : values()) {
                if (type.boardId.equals(boardId)) {
                    return type;
                }
            }
        }
        throw new BoardException("존재하지 않는 게시판입니다. (boardId: " + boardId + ")", HttpStatus.NOT_FOUND);
    }
}
