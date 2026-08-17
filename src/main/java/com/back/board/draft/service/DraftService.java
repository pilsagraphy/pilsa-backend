package com.back.board.draft.service;

import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListResponse;
import com.back.board.draft.dto.DraftResponse;
import com.back.board.draft.dto.DraftSaveRequest;
import com.back.board.draft.dto.DraftSaveResponse;
import com.back.board.draft.dto.PreUploadResponse;
import com.back.board.draft.dto.ServeFileInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 게시판 글쓰기 임시저장(draft) + 선업로드(에디터 이미지/초안 첨부).
 * 회원당 최대 5개 슬롯. 남의/없는 draftId 는 전부 404 (존재 여부 노출 방지).
 */
public interface DraftService {

    // (3) 신규 저장 — 빈 슬롯에 INSERT
    DraftSaveResponse save(Long boardId, DraftSaveRequest request);

    // (4) 덮어쓰기 — 슬롯 유지 UPDATE
    DraftResponse overwrite(Long boardId, Long draftId, DraftSaveRequest request);

    // (1) 내 임시저장 목록
    DraftListResponse list(Long boardId);

    // (2) 단건 불러오기
    DraftDetailResponse get(Long boardId, Long draftId);

    // (5) 단건 삭제 (물리 파일 포함)
    DraftResponse delete(Long boardId, Long draftId);

    // 선업로드 — 에디터 본문 이미지
    PreUploadResponse uploadImage(Long boardId, MultipartFile file);

    // 선업로드 — 초안 일반 첨부
    PreUploadResponse uploadAttachment(Long boardId, MultipartFile file);

    // /files/{attachmentId} 서빙 정보 (없으면 404)
    ServeFileInfo getServeInfo(Long attachmentId);
}
