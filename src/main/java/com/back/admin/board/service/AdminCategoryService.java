package com.back.admin.board.service;

import com.back.admin.board.dto.AdminCategoryResponse;
import com.back.admin.board.dto.CategorySaveRequest;
import com.back.admin.board.mapper.AdminBoardMapper;
import com.back.admin.board.mapper.AdminCategoryMapper;
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
import java.util.Locale;
import java.util.UUID;

/**
 * 게시판별 카테고리(태그) 관리.
 *
 * 카테고리는 지금까지 DB 를 직접 고쳐야만 바뀌었다 — 관리자가 게시판을 만들어도 그 게시판에서 쓸 태그는
 * 손댈 수 없었다는 뜻이다. 게시판이 데이터가 된 것과 같은 이유로 카테고리도 데이터여야 한다.
 *
 * 두 가지가 이 표의 성격을 정한다:
 *  - '중요'(code=PINNED)는 상단 고정을 고르는 통로다. 이름을 바꾸거나 지우면 그 게시판에서 고정을 못 쓴다 → 잠근다.
 *  - (board_id, name) 유니크가 is_active 를 보지 않는다. 끈 카테고리가 이름을 계속 쥐고 있으므로
 *    같은 이름을 다시 등록하면 새로 넣는 대신 그 줄을 되살린다(글에 달려 있던 배지도 함께 돌아온다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCategoryService {

    private static final String PINNED_CODE = "PINNED";
    private static final int MAX_NAME_LENGTH = 50; // categories.name varchar(50)

    private final AdminCategoryMapper adminCategoryMapper;
    private final AdminBoardMapper adminBoardMapper;
    private final BoardMapper boardMapper;

    @Transactional
    public List<AdminCategoryResponse> getCategories(Long boardId) {
        AuthUtils.requireAdmin();
        requireBoard(boardId);
        // '중요'는 모든 게시판에 있어야 한다(상단 고정 통로). 게시판 생성 시에만 만들다 보니 그 전에 만든
        // 게시판(공지·자유·정보)에는 없었다 — 목록을 열 때마다 없으면 채운다 (INSERT IGNORE 라 있으면 무시)
        adminBoardMapper.insertPinnedCategory(boardId);
        return adminCategoryMapper.findCategories(boardId);
    }

    @Transactional
    public AdminCategoryResponse createCategory(Long boardId, CategorySaveRequest request) {
        AuthUtils.requireAdmin();
        requireBoard(boardId);

        String name = requireName(request.getName());
        if (adminCategoryMapper.existsActiveName(boardId, name, null)) {
            throw new BoardException("이미 있는 카테고리 이름입니다.", HttpStatus.CONFLICT);
        }

        // 예전에 지운 이름이면 새로 넣지 못한다(유니크가 is_active 를 안 본다) → 그 줄을 되살린다
        Long inactiveId = adminCategoryMapper.findInactiveIdByName(boardId, name);
        Long categoryId;
        if (inactiveId != null) {
            adminCategoryMapper.reactivateCategory(inactiveId, null);
            categoryId = inactiveId;
            log.info("[관리자] 카테고리 되살림 - boardId: {}, categoryId: {}, name: {}", boardId, categoryId, name);
        } else {
            adminCategoryMapper.insertCategory(boardId, name, newCode(), null);
            categoryId = adminCategoryMapper.findActiveIdByName(boardId, name);
            if (categoryId == null) {
                throw new BoardException("카테고리를 만들지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            log.info("[관리자] 카테고리 생성 - boardId: {}, categoryId: {}, name: {}", boardId, categoryId, name);
        }

        resequence(boardId, categoryId, request.getDisplayOrder());
        return findOrThrow(boardId, categoryId);
    }

    @Transactional
    public AdminCategoryResponse updateCategory(Long boardId, Long categoryId, CategorySaveRequest request) {
        AuthUtils.requireAdmin();
        requireBoard(boardId);

        AdminCategoryResponse current = findOrThrow(boardId, categoryId);
        requireNotPinned(current, "이름을 바꿀 수 없습니다");

        if (request.getName() != null) {
            String name = requireName(request.getName());
            if (adminCategoryMapper.existsActiveName(boardId, name, categoryId)) {
                throw new BoardException("이미 있는 카테고리 이름입니다.", HttpStatus.CONFLICT);
            }
            // 같은 이름으로 꺼져 있는 줄이 있으면 유니크에 걸린다. 그 줄은 이미 쓸모가 없으니 이름을 비켜 준다.
            Long inactiveId = adminCategoryMapper.findInactiveIdByName(boardId, name);
            if (inactiveId != null && !inactiveId.equals(categoryId)) {
                adminCategoryMapper.updateName(inactiveId, name + "_" + inactiveId);
            }
            adminCategoryMapper.updateName(categoryId, name);
        }

        if (request.getDisplayOrder() != null) {
            resequence(boardId, categoryId, request.getDisplayOrder());
        }
        return findOrThrow(boardId, categoryId);
    }

    @Transactional
    public void deleteCategory(Long boardId, Long categoryId) {
        AuthUtils.requireAdmin();
        BoardPolicy board = requireBoard(boardId);

        AdminCategoryResponse current = findOrThrow(boardId, categoryId);
        requireNotPinned(current, "지울 수 없습니다");

        // 글이 달고 있으면 지우지 않는다. 지워 버리면 그 글들의 배지만 조용히 사라진다 —
        // 글을 먼저 옮기게 하는 편이 눈에 보인다.
        int postCount = adminCategoryMapper.countPostsByCategory(categoryId);
        if (postCount > 0) {
            throw new BoardException("게시글 " + postCount + "건이 이 카테고리를 쓰고 있어 삭제할 수 없습니다.",
                    HttpStatus.CONFLICT);
        }
        // 이 게시판의 기본 카테고리면 글쓰기가 없는 값을 가리키게 된다
        if (categoryId.equals(board.getDefaultCategoryId())) {
            throw new BoardException("이 게시판의 기본 카테고리라 삭제할 수 없습니다. 기본 카테고리를 먼저 바꿔 주세요.",
                    HttpStatus.CONFLICT);
        }

        adminCategoryMapper.deactivateCategory(categoryId);
        resequence(boardId, null, null);
        log.info("[관리자] 카테고리 삭제 - boardId: {}, categoryId: {}", boardId, categoryId);
    }

    // ───────────────────────────── 도우미 ─────────────────────────────

    /**
     * 카테고리 순번을 1..N 으로 다시 채운다 (게시판 순서와 같은 방식).
     * '중요'는 늘 맨 뒤(99)라 줄 세우기에서 빠져 있다.
     *
     * @param targetId 자리를 옮길 카테고리. null 이면 순서를 유지한 채 번호만 정리한다.
     * @param position 옮길 자리(1부터). null 이면 맨 뒤.
     */
    private void resequence(Long boardId, Long targetId, Integer position) {
        List<Long> ids = new ArrayList<>(adminCategoryMapper.findLiveCategoryIdsOrdered(boardId));
        if (targetId != null && ids.remove(targetId)) {
            int index = (position == null) ? ids.size() : Math.max(0, Math.min(ids.size(), position - 1));
            ids.add(index, targetId);
        }
        if (!ids.isEmpty()) {
            adminCategoryMapper.applyDisplayOrder(ids);
        }
    }

    private BoardPolicy requireBoard(Long boardId) {
        BoardPolicy board = boardMapper.findBoardPolicy(boardId);
        if (board == null) {
            throw new BoardException("존재하지 않는 게시판입니다.", HttpStatus.NOT_FOUND);
        }
        return board;
    }

    private AdminCategoryResponse findOrThrow(Long boardId, Long categoryId) {
        AdminCategoryResponse category = adminCategoryMapper.findById(boardId, categoryId);
        if (category == null) {
            throw new BoardException("존재하지 않는 카테고리입니다.", HttpStatus.NOT_FOUND);
        }
        return category;
    }

    private String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BoardException("카테고리 이름은 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BoardException("카테고리 이름은 " + MAX_NAME_LENGTH + "자까지 쓸 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private void requireNotPinned(AdminCategoryResponse category, String what) {
        if (PINNED_CODE.equals(category.getCode())) {
            throw new BoardException("'중요'는 상단 고정에 쓰이는 카테고리라 " + what + ".", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 새 카테고리의 code. 화면에는 쓰이지 않고 (board_id, code) 유니크를 채우기 위한 값이다.
     * 이름이 한글이라 이름에서 만들 수 없어 무작위로 준다. varchar(20) 안에 들어간다.
     */
    private String newCode() {
        return "CAT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
