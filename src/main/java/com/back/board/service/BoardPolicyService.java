package com.back.board.service;

import com.back.board.dto.BoardPolicy;
import com.back.board.dto.BoardSummaryResponse;
import com.back.board.exception.BoardException;
import com.back.board.mapper.BoardMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 현재 사용자가 열람 가능한 게시판 목록 (네비게이션용).
     * 게시판이 데이터가 되었으므로 프론트가 메뉴를 하드코딩할 수 없어 이 API가 필요하다.
     */
    public List<BoardSummaryResponse> getReadableBoards() {
        String memberType = AuthUtils.memberType();
        int adminLevel = AuthUtils.adminLevel();

        List<BoardSummaryResponse> result = new ArrayList<>();
        for (BoardPolicy policy : boardMapper.findBoardPolicies()) {
            if (!policy.canRead(memberType, adminLevel)) {
                continue;
            }
            BoardSummaryResponse summary = new BoardSummaryResponse();
            summary.setBoardId(policy.getBoardId());
            summary.setBoardName(policy.getName());
            summary.setDisplayOrder(policy.getDisplayOrder());
            summary.setCanWrite(policy.canWrite(memberType, adminLevel));
            summary.setAllowComment(policy.getAllowComment());
            summary.setAllowAttachment(policy.getAllowAttachment());
            summary.setCategoryMode(policy.getCategoryMode());
            summary.setAllowAnonymous(policy.getAllowAnonymous());
            summary.setAllowPrivateComment(policy.getAllowPrivateComment());
            result.add(summary);
        }
        return result;
    }

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
