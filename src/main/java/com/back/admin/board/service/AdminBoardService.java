package com.back.admin.board.service;

import com.back.admin.board.dto.AdminBoardResponse;
import com.back.admin.board.dto.BoardSaveRequest;
import com.back.board.dto.BoardPolicy;
import com.back.board.exception.BoardException;
import com.back.board.mapper.BoardMapper;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 게시판 관리 서비스.
 * 게시판이 데이터가 되었으므로(=BoardType enum 제거) 관리자가 만든 게시판도
 * 별도 배포 없이 /api/stu/{boardId}/** 로 즉시 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBoardService {

    private static final Set<String> ALLOWED_READ_SCOPES =
            Set.of(BoardPolicy.SCOPE_ALL, BoardPolicy.SCOPE_MEMBER, BoardPolicy.SCOPE_STUDENT, BoardPolicy.SCOPE_ALUMNI);
    private static final int MIN_WRITE_LEVEL = 0;
    private static final int MAX_WRITE_LEVEL = 3;

    private final BoardMapper boardMapper;

    public List<AdminBoardResponse> getBoards() {
        AuthUtils.requireAdmin();
        List<AdminBoardResponse> result = new ArrayList<>();
        for (BoardPolicy policy : boardMapper.findBoardPolicies()) {
            result.add(toResponse(policy, boardMapper.countPostsByBoard(policy.getBoardId())));
        }
        return result;
    }

    @Transactional
    public AdminBoardResponse createBoard(BoardSaveRequest request) {
        AuthUtils.requireAdmin();

        if (request.getName() == null || request.getName().isBlank()) {
            throw new BoardException("게시판 이름은 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        String name = request.getName().trim();
        if (boardMapper.existsBoardName(name, null)) {
            throw new BoardException("이미 존재하는 게시판 이름입니다.", HttpStatus.CONFLICT);
        }
        validateScopeAndLevel(request);

        BoardPolicy board = new BoardPolicy();
        board.setName(name);
        // 기본값: 로그인 회원 열람 / 일반회원 작성 / 댓글·첨부 사용 / 카테고리 미사용
        board.setReadScope(request.getReadScope() != null ? request.getReadScope() : BoardPolicy.SCOPE_MEMBER);
        board.setWriteLevel(request.getWriteLevel() != null ? request.getWriteLevel() : 0);
        board.setDisplayOrder(request.getDisplayOrder());
        board.setAllowComment(request.getAllowComment() == null || request.getAllowComment());
        board.setAllowAttachment(request.getAllowAttachment() == null || request.getAllowAttachment());
        board.setCategoryMode(Boolean.TRUE.equals(request.getCategoryMode()));
        board.setDefaultCategoryId(request.getDefaultCategoryId());
        board.setAllowAnonymous(Boolean.TRUE.equals(request.getAllowAnonymous()));
        board.setAllowPrivateComment(Boolean.TRUE.equals(request.getAllowPrivateComment()));

        boardMapper.insertBoard(board);
        log.info("게시판 생성 완료 - boardId: {}, name: {}", board.getBoardId(), name);
        return toResponse(boardMapper.findBoardPolicy(board.getBoardId()), 0);
    }

    @Transactional
    public AdminBoardResponse updateBoard(Long boardId, BoardSaveRequest request) {
        AuthUtils.requireAdmin();

        BoardPolicy current = boardMapper.findBoardPolicy(boardId);
        if (current == null) {
            throw new BoardException("존재하지 않는 게시판입니다.", HttpStatus.NOT_FOUND);
        }
        validateScopeAndLevel(request);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new BoardException("게시판 이름은 비울 수 없습니다.", HttpStatus.BAD_REQUEST);
            }
            if (boardMapper.existsBoardName(name, boardId)) {
                throw new BoardException("이미 존재하는 게시판 이름입니다.", HttpStatus.CONFLICT);
            }
            request.setName(name);
        }

        BoardPolicy patch = new BoardPolicy();
        patch.setName(request.getName());
        patch.setReadScope(request.getReadScope());
        patch.setWriteLevel(request.getWriteLevel());
        patch.setDisplayOrder(request.getDisplayOrder());
        patch.setAllowComment(request.getAllowComment());
        patch.setAllowAttachment(request.getAllowAttachment());
        patch.setCategoryMode(request.getCategoryMode());
        patch.setDefaultCategoryId(request.getDefaultCategoryId());
        patch.setAllowAnonymous(request.getAllowAnonymous());
        patch.setAllowPrivateComment(request.getAllowPrivateComment());

        boardMapper.updateBoard(boardId, patch);
        return toResponse(boardMapper.findBoardPolicy(boardId), boardMapper.countPostsByBoard(boardId));
    }

    @Transactional
    public void deleteBoard(Long boardId) {
        AuthUtils.requireAdmin();

        if (boardMapper.findBoardPolicy(boardId) == null) {
            throw new BoardException("존재하지 않는 게시판입니다.", HttpStatus.NOT_FOUND);
        }
        // 글이 남아 있으면 삭제 금지 (글을 먼저 정리하게 유도 — 고아 게시글 방지)
        int postCount = boardMapper.countPostsByBoard(boardId);
        if (postCount > 0) {
            throw new BoardException("게시글이 " + postCount + "건 남아 있어 삭제할 수 없습니다.", HttpStatus.CONFLICT);
        }
        boardMapper.deleteBoard(boardId);
    }

    private void validateScopeAndLevel(BoardSaveRequest request) {
        if (request.getReadScope() != null && !ALLOWED_READ_SCOPES.contains(request.getReadScope())) {
            throw new BoardException("열람 권한 값이 올바르지 않습니다. (ALL/MEMBER/STUDENT/ALUMNI)", HttpStatus.BAD_REQUEST);
        }
        if (request.getWriteLevel() != null
                && (request.getWriteLevel() < MIN_WRITE_LEVEL || request.getWriteLevel() > MAX_WRITE_LEVEL)) {
            throw new BoardException("작성 권한 레벨은 0~3 이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private AdminBoardResponse toResponse(BoardPolicy policy, int postCount) {
        AdminBoardResponse response = new AdminBoardResponse();
        response.setBoardId(policy.getBoardId());
        response.setName(policy.getName());
        response.setPostCount(postCount);
        response.setReadScope(policy.getReadScope());
        response.setWriteLevel(policy.getWriteLevel());
        response.setDisplayOrder(policy.getDisplayOrder());
        response.setAllowComment(policy.getAllowComment());
        response.setAllowAttachment(policy.getAllowAttachment());
        response.setCategoryMode(policy.getCategoryMode());
        response.setDefaultCategoryId(policy.getDefaultCategoryId());
        response.setAllowAnonymous(policy.getAllowAnonymous());
        response.setAllowPrivateComment(policy.getAllowPrivateComment());
        return response;
    }
}
