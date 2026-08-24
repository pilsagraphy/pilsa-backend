package com.back.board.draft.service;

import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListResponse;
import com.back.board.draft.dto.DraftRequest;
import com.back.board.draft.dto.DraftResponse;

import java.util.List;

/**
 * 게시판 임시저장(Draft) 서비스.
 *
 * 경로는 게시글과 같은 /api/user/boards/{boardId} 아래에 둔다 — 초안은 "게시판 글쓰기의 일부"다(SPEC-A5).
 * 상한(회원×게시판당 5개)은 DB(uq_drafts_user_board_slot)가 물리 강제하고, 서비스는 빈 슬롯을 찾아 저장한다.
 */
public interface DraftService {

    // 내 임시저장 목록 (게시판별, 최근 저장순). limit 는 선택(기본 전체)
    List<DraftListResponse> getDrafts(Long boardId, Integer limit);

    // 단건 불러오기 (이어쓰기) — 본인 것만, 아니면 404
    DraftDetailResponse getDraft(Long boardId, Long draftId);

    // 신규 저장 (빈 슬롯에 INSERT). 상한 초과 시 409
    DraftResponse createDraft(Long boardId, DraftRequest request);

    // 덮어쓰기 (슬롯 유지, updatedAt 갱신) — 본인 것만, 아니면 404
    DraftResponse updateDraft(Long boardId, Long draftId, DraftRequest request);

    // 단건 삭제 (물리 삭제 + 첨부 물리 파일까지) — 본인 것만, 아니면 404
    DraftResponse deleteDraft(Long boardId, Long draftId);
}
