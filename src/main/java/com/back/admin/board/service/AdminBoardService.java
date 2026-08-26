package com.back.admin.board.service;

import com.back.admin.board.dto.AdminBoardResponse;
import com.back.admin.board.dto.BoardSaveRequest;
import com.back.board.dto.BoardPolicy;
import com.back.board.exception.BoardException;
import com.back.admin.board.mapper.AdminBoardMapper;
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
 * 관리 전용 쿼리는 이 패키지의 AdminBoardMapper 가 갖는다.
 * 게시판이 데이터가 되었으므로(=BoardType enum 제거) 관리자가 만든 게시판도
 * 별도 배포 없이 /api/user/boards/{boardId}/** 로 즉시 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBoardService {

    // 게시판 열람 대상은 반드시 로그인 회원 이상이다 — 전체 공개(ALL)는 선택지에서 제외한다
    private static final Set<String> ALLOWED_READ_SCOPES =
            Set.of(BoardPolicy.SCOPE_MEMBER, BoardPolicy.SCOPE_STUDENT, BoardPolicy.SCOPE_ALUMNI);
    private static final int MIN_WRITE_LEVEL = 0;
    private static final int MAX_WRITE_LEVEL = 3;

    private final AdminBoardMapper adminBoardMapper;
    // 게시판 정책 조회는 회원 판정과 같은 쿼리를 쓰므로 board 도메인 매퍼를 재사용한다
    private final BoardMapper boardMapper;

    public List<AdminBoardResponse> getBoards() {
        AuthUtils.requireAdmin();
        List<AdminBoardResponse> result = new ArrayList<>();
        for (BoardPolicy policy : boardMapper.findBoardPolicies()) {
            result.add(toResponse(policy, adminBoardMapper.countPostsByBoard(policy.getBoardId())));
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
        if (adminBoardMapper.existsBoardName(name, null)) {
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

        adminBoardMapper.insertBoard(board);

        // 모든 게시판이 '중요'(code=PINNED) 카테고리를 갖도록 자동 생성한다.
        // 상단 고정은 이 카테고리 선택으로만 결정되므로, 없으면 새 게시판에서 고정을 못 쓴다.
        adminBoardMapper.insertPinnedCategory(board.getBoardId());

        // 요청에 위치가 있으면 그 자리에, 없으면 맨 뒤에 놓고 전체 순번을 1..N 으로 다시 채운다
        resequence(board.getBoardId(), request.getDisplayOrder());

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
            if (adminBoardMapper.existsBoardName(name, boardId)) {
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

        adminBoardMapper.updateBoard(boardId, patch);

        // displayOrder 는 "몇 번째"다 — 그 자리로 옮기고 나머지를 한 칸씩 밀어 1..N 을 유지한다
        if (request.getDisplayOrder() != null) {
            resequence(boardId, request.getDisplayOrder());
        }
        return toResponse(boardMapper.findBoardPolicy(boardId), adminBoardMapper.countPostsByBoard(boardId));
    }

    @Transactional
    public void deleteBoard(Long boardId) {
        AuthUtils.requireAdmin();

        if (boardMapper.findBoardPolicy(boardId) == null) {
            throw new BoardException("존재하지 않는 게시판입니다.", HttpStatus.NOT_FOUND);
        }
        // 글이 남아 있으면 삭제 금지 (글을 먼저 정리하게 유도 — 고아 게시글 방지)
        int postCount = adminBoardMapper.countPostsByBoard(boardId);
        if (postCount > 0) {
            throw new BoardException("게시글이 " + postCount + "건 남아 있어 삭제할 수 없습니다.", HttpStatus.CONFLICT);
        }
        adminBoardMapper.deleteBoard(boardId);

        // 빠져나간 자리만큼 뒤 게시판을 당겨 번호에 구멍을 남기지 않는다
        resequence(null, null);
    }

    /**
     * 살아있는 게시판의 display_order 를 1..N 으로 다시 채운다.
     *
     * 생성·삭제·순서 변경 뒤 항상 호출한다. 번호가 늘 조밀해야 프론트가 드래그한 결과를
     * "몇 번째"라는 값 하나로만 보낼 수 있다 — 구멍이 있으면 화면의 N번째와 display_order 값이
     * 어긋나 엉뚱한 게시판이 밀린다.
     *
     * @param targetBoardId 자리를 옮길 게시판. null 이면 현재 순서를 유지한 채 번호만 정리한다.
     * @param position      옮길 자리(1부터). null 이면 맨 뒤. 범위를 벗어나면 처음/끝으로 맞춘다.
     */
    private void resequence(Long targetBoardId, Integer position) {
        List<Long> ids = new ArrayList<>(adminBoardMapper.findLiveBoardIdsOrdered());
        if (targetBoardId != null && ids.remove(targetBoardId)) {
            int index = (position == null)
                    ? ids.size()
                    : Math.max(0, Math.min(ids.size(), position - 1));
            ids.add(index, targetBoardId);
        }
        if (!ids.isEmpty()) {
            adminBoardMapper.applyDisplayOrder(ids);
        }
    }

    private void validateScopeAndLevel(BoardSaveRequest request) {
        if (request.getReadScope() != null && !ALLOWED_READ_SCOPES.contains(request.getReadScope())) {
            throw new BoardException("열람 권한 값이 올바르지 않습니다. (MEMBER=재학+졸업 / STUDENT=재학 / ALUMNI=졸업)", HttpStatus.BAD_REQUEST);
        }
        if (request.getWriteLevel() != null
                && (request.getWriteLevel() < MIN_WRITE_LEVEL || request.getWriteLevel() > MAX_WRITE_LEVEL)) {
            throw new BoardException("작성 권한 레벨은 0~3 이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private AdminBoardResponse toResponse(BoardPolicy policy, int postCount) {
        AdminBoardResponse response = new AdminBoardResponse();
        response.setBoardId(policy.getBoardId());
        response.setBoardName(policy.getName());
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
