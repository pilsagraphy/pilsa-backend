package com.back.board.service;

import com.back.board.dto.BoardPolicy;
import com.back.board.exception.BoardException;
import com.back.board.mapper.BoardMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 정책 조회 + 접근 권한 판정.
 *
 * 권한은 URL 패턴이 아니라 데이터(boards.read_scope / write_level)로 판정한다.
 * 관리자가 새 게시판을 만들면 코드 변경 없이 그 게시판의 정책이 그대로 적용된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardPolicyService {

    private final BoardMapper boardMapper;

    // 게시판 조회 (삭제된 게시판은 없는 것으로 취급)
    public BoardPolicy get(Long boardId) {
        BoardPolicy policy = boardMapper.findBoardPolicy(boardId);
        if (policy == null) {
            throw new BoardException("존재하지 않는 게시판입니다. (boardId: " + boardId + ")", HttpStatus.NOT_FOUND);
        }
        return policy;
    }

    // 열람 권한 확인 후 정책 반환
    public BoardPolicy requireReadable(Long boardId) {
        BoardPolicy policy = get(boardId);
        if (!policy.canRead(AuthUtils.memberType(), AuthUtils.adminLevel())) {
            throw new BoardException("이 게시판을 열람할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        return policy;
    }

    // 작성 권한 확인 후 정책 반환
    public BoardPolicy requireWritable(Long boardId) {
        BoardPolicy policy = get(boardId);
        if (!policy.canWrite(AuthUtils.memberType(), AuthUtils.adminLevel())) {
            throw new BoardException("이 게시판에 글을 등록할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        return policy;
    }
}
