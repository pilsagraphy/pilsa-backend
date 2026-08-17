package com.back.board.draft.service;

import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListResponse;
import com.back.board.draft.dto.DraftResponse;
import com.back.board.draft.dto.DraftSaveRequest;
import com.back.board.draft.dto.DraftSaveResponse;
import com.back.board.draft.dto.PendingAttachment;
import com.back.board.draft.dto.PreUploadResponse;
import com.back.board.draft.dto.ServeFileInfo;
import com.back.board.draft.mapper.DraftMapper;
import com.back.board.draft.util.ContentAttachmentParser;
import com.back.board.dto.BoardPolicy;
import com.back.board.exception.BoardException;
import com.back.board.service.BoardPolicyService;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 임시저장(draft) 서비스.
 *
 * 슬롯은 회원당 1~5로, drafts.uq_drafts_user_slot(UNIQUE)이 물리적으로 5개를 강제한다.
 * 첨부/본문이미지는 "선업로드된 대기 첨부(post_id·draft_id 둘 다 NULL)"를 저장 시점에 이 초안으로
 * 귀속(reconcile)시키는 구조다 — 본문에서 빠진 이미지는 그 순간 DB 행 + 물리 파일까지 정리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DraftServiceImpl implements DraftService {

    private static final int MAX_SLOT = 5;

    private final DraftMapper draftMapper;
    private final BoardPolicyService boardPolicyService;
    private final FileStorageUtil fileStorageUtil;

    // ---------- 저장 / 덮어쓰기 ----------

    @Override
    @Transactional
    public DraftSaveResponse save(Long boardId, DraftSaveRequest request) {
        boardPolicyService.requireWritable(boardId); // 쓰기 권한 없는 게시판(예: 공지)에 일반 회원 저장 → 403
        Long userId = AuthUtils.currentUserId();
        requireHasContent(request);

        int slot = findFreeSlot(userId); // 5개가 다 차 있으면 409

        try {
            draftMapper.insertDraft(userId, slot, boardId, request);
        } catch (DataIntegrityViolationException e) {
            // 슬롯 계산과 INSERT 사이의 경합으로 UNIQUE(user_id, slot_no) 충돌 — 상한 도달로 간주
            throw slotFullException();
        }

        Long draftId = request.getDraftId();
        reconcileAttachments(draftId, userId, request);
        return new DraftSaveResponse("임시저장했습니다.", draftId, slot);
    }

    @Override
    @Transactional
    public DraftResponse overwrite(Long boardId, Long draftId, DraftSaveRequest request) {
        Long userId = AuthUtils.currentUserId();
        requireHasContent(request);
        requireOwnedDraft(draftId, userId, boardId); // 남의 것/없음/게시판 불일치 → 404

        draftMapper.updateDraft(draftId, userId, request);
        reconcileAttachments(draftId, userId, request);
        return new DraftResponse("임시저장을 덮어썼습니다.");
    }

    // ---------- 조회 ----------

    @Override
    public DraftListResponse list(Long boardId) {
        // 슬롯은 회원 단위(게시판 무관)라 목록도 user_id 기준으로 내려준다(각 초안이 자기 board_id 를 들고 있음)
        Long userId = AuthUtils.currentUserId();
        return new DraftListResponse(draftMapper.findDraftsByUser(userId));
    }

    @Override
    public DraftDetailResponse get(Long boardId, Long draftId) {
        Long userId = AuthUtils.currentUserId();
        DraftDetailResponse detail = draftMapper.findDraftDetail(draftId, userId);
        if (detail == null || !boardId.equals(detail.getBoardId())) {
            throw notFound(); // 남의 것/없음/경로 게시판 불일치 → 전부 404
        }
        // 일반 첨부만 (본문 이미지는 content HTML 에 이미 들어 있어 중복 노출하지 않는다)
        detail.setAttachments(draftMapper.findFileAttachmentsByDraftId(draftId));
        return detail;
    }

    // ---------- 삭제 (물리 파일 포함) ----------

    @Override
    @Transactional
    public DraftResponse delete(Long boardId, Long draftId) {
        Long userId = AuthUtils.currentUserId();
        requireOwnedDraft(draftId, userId, boardId);

        // 순서: 행이 살아있을 때 파일 경로를 먼저 확보 → DELETE(CASCADE로 첨부행 정리) → 물리 파일 삭제
        List<String> fileUrls = draftMapper.findAttachmentUrlsByDraftId(draftId);
        draftMapper.deleteDraft(draftId, userId);
        deletePhysicalFiles(fileUrls);
        return new DraftResponse("임시저장을 삭제했습니다.");
    }

    // ---------- 선업로드 ----------

    @Override
    @Transactional
    public PreUploadResponse uploadImage(Long boardId, MultipartFile file) {
        BoardPolicy policy = boardPolicyService.requireWritable(boardId);
        requireNonEmpty(file);
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BoardException("이미지 파일만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        return storePending(policy, file, "image");
    }

    @Override
    @Transactional
    public PreUploadResponse uploadAttachment(Long boardId, MultipartFile file) {
        BoardPolicy policy = boardPolicyService.requireWritable(boardId);
        requireNonEmpty(file);
        return storePending(policy, file, "file");
    }

    @Override
    public ServeFileInfo getServeInfo(Long attachmentId) {
        ServeFileInfo info = draftMapper.findServeFileInfo(attachmentId);
        if (info == null) {
            throw new BoardException("존재하지 않는 파일입니다.", HttpStatus.NOT_FOUND);
        }
        return info;
    }

    // ---------- 내부 헬퍼 ----------

    /**
     * 본문 이미지 + 요청 첨부를 이 초안으로 재조정한다.
     *  1. content HTML 이 참조하는 이미지 attachment_id + 요청의 첨부 id 를 합쳐 유지 집합 R 을 만든다
     *  2. R 을 이 초안으로 귀속(대기/내 것만) — draft_id 만 세팅하므로 완화 CHECK 만족
     *  3. 이전엔 이 초안 소유였으나 R 에 없는 것(본문에서 지운 이미지/떼어낸 첨부)은 DB 행 + 물리 파일 삭제
     */
    private void reconcileAttachments(Long draftId, Long userId, DraftSaveRequest request) {
        Set<Long> keep = new LinkedHashSet<>(ContentAttachmentParser.extractAttachmentIds(request.getContent()));
        if (!CollectionUtils.isEmpty(request.getAttachmentIds())) {
            keep.addAll(request.getAttachmentIds());
        }
        List<Long> keepList = new ArrayList<>(keep);

        if (!keepList.isEmpty()) {
            draftMapper.bindAttachmentsToDraft(draftId, userId, keepList);
        }
        // 참조 끊긴 것 정리: 경로 먼저 확보 → 행 삭제 → 물리 파일 삭제
        List<String> orphanUrls = draftMapper.findDraftAttachmentUrlsNotIn(draftId, keepList);
        draftMapper.deleteDraftAttachmentsNotIn(draftId, keepList);
        deletePhysicalFiles(orphanUrls);
    }

    private PreUploadResponse storePending(BoardPolicy policy, MultipartFile file, String attachmentType) {
        Long userId = AuthUtils.currentUserId();
        // 초안 업로드분도 발행 후 경로와 어긋나지 않도록 게시판 폴더 아래(pending)에 둔다.
        // 소유권 이전(발행)은 DB 만 바꾸고 파일은 옮기지 않으므로, 서빙은 /files/{id} 로 간접 참조한다.
        String dir = policy.uploadDir() + "/pending";
        String savedUrl = fileStorageUtil.save(file, dir);

        PendingAttachment a = new PendingAttachment();
        a.setUploadedBy(userId);
        a.setOriginName(file.getOriginalFilename());
        a.setFileUrl(savedUrl);
        a.setFileSize(file.getSize());
        a.setFileType(file.getContentType());
        a.setAttachmentType(attachmentType);
        draftMapper.insertPendingAttachment(a);

        // URL 은 소유자(draft/post)와 무관하게 안정적인 /files/{id}. 발행돼도 깨지지 않는다.
        String url = "/files/" + a.getAttachmentId();
        return new PreUploadResponse(a.getAttachmentId(), url, a.getOriginName(), a.getFileSize(), attachmentType);
    }

    /** 빈 슬롯(1~5) 중 가장 작은 번호. 5개가 다 차 있으면 409 */
    private int findFreeSlot(Long userId) {
        Set<Integer> used = new HashSet<>(draftMapper.findUsedSlots(userId));
        for (int slot = 1; slot <= MAX_SLOT; slot++) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        throw slotFullException();
    }

    private void requireOwnedDraft(Long draftId, Long userId, Long boardId) {
        if (draftMapper.findOwnedDraftId(draftId, userId, boardId) == null) {
            throw notFound();
        }
    }

    // 제목·본문이 둘 다 비면 저장할 내용이 없다 (공백만 있는 경우도 빈 것으로 본다)
    private void requireHasContent(DraftSaveRequest request) {
        boolean titleBlank = request.getTitle() == null || request.getTitle().isBlank();
        boolean contentBlank = request.getContent() == null || request.getContent().isBlank();
        if (titleBlank && contentBlank) {
            throw new BoardException("저장할 내용이 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireNonEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BoardException("업로드할 파일이 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void deletePhysicalFiles(List<String> fileUrls) {
        if (CollectionUtils.isEmpty(fileUrls)) {
            return;
        }
        // 물리 삭제 실패는 FileStorageUtil 이 로그만 남긴다 (재시도/청소 배치가 후속 정리)
        fileUrls.forEach(fileStorageUtil::delete);
    }

    private BoardException notFound() {
        return new BoardException("존재하지 않는 임시저장입니다.", HttpStatus.NOT_FOUND);
    }

    private BoardException slotFullException() {
        return new BoardException("임시저장은 최대 " + MAX_SLOT + "개까지 가능합니다. 기존 임시저장을 삭제해 주세요.",
                HttpStatus.CONFLICT);
    }
}
