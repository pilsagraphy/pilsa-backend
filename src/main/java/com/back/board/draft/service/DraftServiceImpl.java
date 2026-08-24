package com.back.board.draft.service;

import com.back.board.attachment.service.AttachmentService;
import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListResponse;
import com.back.board.draft.dto.DraftRequest;
import com.back.board.draft.dto.DraftResponse;
import com.back.board.draft.mapper.DraftMapper;
import com.back.board.exception.BoardException;
import com.back.board.service.BoardPolicyService;
import com.back.global.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 임시저장 서비스 구현.
 *
 * 설계 원칙
 *  - 소프트삭제 예외: 초안은 발행 전 개인 작업물이라 물리 DELETE(state 컬럼 없음). 첨부도 같은 규칙.
 *  - 남의/없는/다른 게시판 초안 접근은 전부 404(존재 노출 방지) — 모든 쿼리에 user_id(+board_id)를 함께 건다.
 *  - 게시판 정책(익명 허용·카테고리 유효성)은 저장 시점에 보정하지 않고 발행 시점에만 검증(SPEC-A5 §1).
 *    저장 시 검사하는 것은 "쓰기 권한(create 만)"과 "제목·본문 동시 공백"뿐.
 *  - 상한은 DB(uq_drafts_user_board_slot)가 물리 강제 — 앱은 draft_max_count 로 빈 슬롯을 탐색하고,
 *    경합으로 슬롯이 겹치면 duplicate-key 를 409 로 변환한다.
 *  - 첨부는 선업로드(POST .../files) 체계와 한 몸 — 재조정·삭제·발행 이관은 전부 AttachmentService 가 담당한다.
 *    초안 삭제 시 선업로드된 파일(본문 이미지·첨부)이 행·물리파일까지 함께 지워진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DraftServiceImpl implements DraftService {

    private static final String POLICY_DRAFT_MAX_COUNT = "draft_max_count";
    private static final int DEFAULT_DRAFT_MAX_COUNT = 5;
    // DB CHECK(slot_no BETWEEN 1 AND 5)가 최종 상한이므로 앱이 탐색하는 슬롯 범위도 이 값을 넘지 않는다
    private static final int SLOT_HARD_CAP = 5;
    // 경합(동시 저장)으로 같은 슬롯을 잡아 duplicate-key 가 날 때의 재시도 횟수
    private static final int SLOT_RETRY = 3;

    private final DraftMapper draftMapper;
    private final BoardPolicyService boardPolicyService;
    private final AttachmentService attachmentService;

    @Override
    public List<DraftListResponse> getDrafts(Long boardId, Integer limit) {
        if (limit != null && limit < 1) {
            // 음수 limit 는 SQL 오류(500)로 떨어지므로 계약대로 400 으로 막는다
            throw new BoardException("limit 는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        Long userId = AuthUtils.currentUserId();
        return draftMapper.findDrafts(userId, boardId, limit);
    }

    @Override
    public DraftDetailResponse getDraft(Long boardId, Long draftId) {
        Long userId = AuthUtils.currentUserId();
        DraftDetailResponse detail = draftMapper.findDraftDetail(draftId, userId, boardId);
        if (detail == null) {
            throw notFound();
        }
        detail.setAttachments(draftMapper.findDraftFileAttachments(draftId));
        return detail;
    }

    @Override
    @Transactional
    public DraftResponse createDraft(Long boardId, DraftRequest request) {
        // 쓰기 권한 없는 게시판(예: 공지사항)에 일반 회원이 초안 저장 → 403
        var policy = boardPolicyService.requireWritable(boardId);
        requireNotEmpty(request);
        Long userId = AuthUtils.currentUserId();

        int maxCount = resolveDraftMaxCount();
        Long draftId = insertIntoFreeSlot(userId, boardId, request, maxCount);

        // 선업로드 파일(본문 이미지·첨부)을 이 초안에 귀속. 본문 마크다운도 함께 훑는다(발행과 같은 규칙).
        // 파일 업로드를 쓰지 않는 게시판이면 귀속 자체를 건너뛴다 — 게시판 정책 우회 방지(발행 경로와 동일 게이트)
        if (policy.isAttachmentAllowed()) {
            attachmentService.reconcileDraftAttachments(draftId, request.getAttachmentIds(), request.getContent());
        }
        return new DraftResponse("임시저장되었습니다.", draftId);
    }

    @Override
    @Transactional
    public DraftResponse updateDraft(Long boardId, Long draftId, DraftRequest request) {
        requireNotEmpty(request);
        Long userId = AuthUtils.currentUserId();

        // 본인 + 해당 게시판인 것만 갱신 (0행이면 없음/남의 것/다른 게시판 → 404).
        // 이 UPDATE 가 drafts 행 락을 잡는다 — 삭제(lockDraft)와 락 순서(drafts → attachments)가 일치한다
        int updated = draftMapper.updateDraft(draftId, userId, boardId, request);
        if (updated == 0) {
            throw notFound();
        }

        // 재조정: 이번 저장의 keep(id 목록 ∪ 본문)에 없는 기존 첨부는 행·물리파일까지 삭제된다
        if (boardPolicyService.get(boardId).isAttachmentAllowed()) {
            attachmentService.reconcileDraftAttachments(draftId, request.getAttachmentIds(), request.getContent());
        }
        return new DraftResponse("임시저장되었습니다.");
    }

    @Override
    @Transactional
    public DraftResponse deleteDraft(Long boardId, Long draftId) {
        Long userId = AuthUtils.currentUserId();

        // 본인 + 해당 게시판 소속 확인 겸 **drafts 행 잠금** (아니면 404 — 존재 노출 방지).
        // 저장(updateDraft)이 drafts 행 락으로 시작하므로 삭제도 같은 순서로 잠가야
        // 데드락(락 순서 역전)과 "삭제 직전 저장이 귀속한 새 첨부가 CASCADE 로만 지워져
        // 파일이 고아로 남는" 경합이 사라진다 — 락을 잡은 뒤 읽는 첨부 목록이 최종본이다
        if (draftMapper.lockDraft(draftId, userId, boardId) == null) {
            throw notFound();
        }

        // 선업로드된 첨부(본문 이미지 포함)를 행·물리파일까지 먼저 지운다 —
        // FK CASCADE 에 맡기면 DB 행만 사라지고 디스크 파일이 고아로 남는다
        attachmentService.deleteDraftAttachments(draftId);
        draftMapper.deleteDraft(draftId, userId);

        return new DraftResponse("삭제되었습니다.");
    }

    // ---- 내부 헬퍼 ----

    /**
     * 빈 슬롯을 찾아 INSERT. 상한이 꽉 찼으면 409.
     * 동시 저장으로 같은 슬롯을 잡아 uq_drafts_user_board_slot 가 걸리면(duplicate-key) 재시도하되,
     * **실패한 슬롯은 로컬로 제외하고 다음 후보로 넘어간다** — REPEATABLE READ 스냅숏에서는
     * 경쟁 트랜잭션이 커밋한 행이 findUsedSlots 재조회에 보이지 않아, 재계산 방식은
     * 같은 슬롯만 계속 시도하다 빈 슬롯이 있는데도 409 를 내기 때문이다.
     */
    private Long insertIntoFreeSlot(Long userId, Long boardId, DraftRequest request, int maxCount) {
        List<Integer> used = draftMapper.findUsedSlots(userId, boardId);
        java.util.Set<Integer> unavailable = new java.util.HashSet<>(used);
        for (int attempt = 0; attempt < SLOT_RETRY; attempt++) {
            Integer slot = findFreeSlot(unavailable, maxCount);
            if (slot == null) {
                throw slotFull(maxCount);
            }
            try {
                // useGeneratedKeys 가 생성된 draft_id 를 request.draftId 에 채운다(insertPost 와 같은 방식)
                draftMapper.insertDraft(userId, boardId, slot, request);
                return request.getDraftId();
            } catch (DuplicateKeyException e) {
                // 동시 저장이 같은 슬롯을 선점한 경우 — 이 슬롯을 제외하고 다음 후보로
                unavailable.add(slot);
                log.debug("초안 슬롯 경합 재시도 - userId:{}, boardId:{}, slot:{}, attempt:{}", userId, boardId, slot, attempt);
            }
        }
        // 재시도 소진 = 사실상 상한까지 참
        throw slotFull(maxCount);
    }

    /** [1..maxCount] 중 사용 불가 집합에 없는 가장 낮은 슬롯. 없으면 null */
    private Integer findFreeSlot(java.util.Set<Integer> unavailable, int maxCount) {
        for (int slot = 1; slot <= maxCount; slot++) {
            if (!unavailable.contains(slot)) {
                return slot;
            }
        }
        return null;
    }

    private void requireNotEmpty(DraftRequest request) {
        if (request.isEmpty()) {
            throw new BoardException("제목과 내용이 모두 비어 있습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private int resolveDraftMaxCount() {
        int max = parseIntOrDefault(draftMapper.findPolicySetting(POLICY_DRAFT_MAX_COUNT), DEFAULT_DRAFT_MAX_COUNT);
        // DB CHECK(1~5)가 최종 상한이므로 그 이상은 탐색해도 INSERT 가 막힌다 → 하드캡으로 자른다
        return Math.min(Math.max(max, 1), SLOT_HARD_CAP);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BoardException notFound() {
        return new BoardException("존재하지 않는 임시저장입니다.", HttpStatus.NOT_FOUND);
    }

    private BoardException slotFull(int maxCount) {
        return new BoardException(
                "임시저장은 최대 " + maxCount + "개까지 보관할 수 있습니다. 오래된 항목을 삭제해 주세요.",
                HttpStatus.CONFLICT);
    }
}
