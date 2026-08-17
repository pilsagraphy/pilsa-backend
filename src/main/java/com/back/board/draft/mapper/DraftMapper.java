package com.back.board.draft.mapper;

import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListItemResponse;
import com.back.board.draft.dto.DraftSaveRequest;
import com.back.board.draft.dto.PendingAttachment;
import com.back.board.draft.dto.ServeFileInfo;
import com.back.board.dto.AttachmentFileResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 임시저장(draft) + 선업로드 첨부/이미지 매퍼.
 *
 * drafts 는 세션성 데이터라 소프트삭제(state)를 쓰지 않고 물리 DELETE 한다.
 * 초안/대기 첨부(post_id NULL)는 학생 목록/상세 쿼리(WHERE post_id=…)에 자연히 걸리지 않는다.
 */
@Mapper
public interface DraftMapper {

    // ---------- 조회 ----------

    /** (1) 내 임시저장 목록 (최근 저장 순). 각 초안의 일반 첨부(file) 개수(attachCnt) 포함 */
    List<DraftListItemResponse> findDraftsByUser(@Param("userId") Long userId);

    /** (2) 단건 불러오기 — 본인 소유일 때만 반환(아니면 null → 서비스가 404) */
    DraftDetailResponse findDraftDetail(@Param("draftId") Long draftId, @Param("userId") Long userId);

    /** (2) 초안의 일반 첨부 목록 (attachment_type='file' 만. 이미지는 content HTML 에 이미 포함) */
    List<AttachmentFileResponse> findFileAttachmentsByDraftId(@Param("draftId") Long draftId);

    /** 소유·게시판 일치 확인용. 본인 초안이고 board_id 도 일치하면 draftId, 아니면 null */
    Long findOwnedDraftId(@Param("draftId") Long draftId,
                          @Param("userId") Long userId,
                          @Param("boardId") Long boardId);

    /** 현재 사용된 슬롯 번호 목록 (빈 슬롯 계산용) */
    List<Integer> findUsedSlots(@Param("userId") Long userId);

    // ---------- 저장/덮어쓰기 ----------

    /**
     * (3) 신규 저장 — 빈 슬롯에 INSERT. 생성된 draft_id 는 keyProperty="req.draftId" 로 되받는다.
     * user_id+slot_no UNIQUE 라 슬롯이 꽉 차 있으면 무결성 예외 → 서비스가 409 로 변환.
     */
    int insertDraft(@Param("userId") Long userId,
                    @Param("slotNo") int slotNo,
                    @Param("boardId") Long boardId,
                    @Param("req") DraftSaveRequest req);

    /** (4) 덮어쓰기 — 슬롯 유지, 본인 소유일 때만 갱신(rows=0 이면 남의 것/없음 → 404) */
    int updateDraft(@Param("draftId") Long draftId,
                    @Param("userId") Long userId,
                    @Param("req") DraftSaveRequest req);

    // ---------- 첨부 재조정(reconcile) ----------

    /**
     * 본문/요청이 참조하는 첨부를 이 초안으로 귀속시킨다.
     *  - uploaded_by = 본인 : 남의 대기 첨부를 가로채지 못하게 막는다
     *  - post_id IS NULL     : 이미 발행된 글의 첨부는 절대 건드리지 않는다
     *  - draft_id IS NULL OR = 이 초안 : 대기중이거나 이미 이 초안 소유인 것만
     * draft_id 만 세팅하고 post_id 는 NULL 로 두므로 완화 CHECK(동시 소유 금지)를 만족한다.
     */
    int bindAttachmentsToDraft(@Param("draftId") Long draftId,
                               @Param("userId") Long userId,
                               @Param("ids") List<Long> ids);

    /** reconcile: 이 초안 소유였으나 지금 참조(keepIds)에 없는 첨부의 물리 경로 (삭제 전 확보) */
    List<String> findDraftAttachmentUrlsNotIn(@Param("draftId") Long draftId,
                                              @Param("keepIds") List<Long> keepIds);

    /** reconcile: 위에서 확보한 대상 행을 물리 DELETE (본문에서 빠진 이미지/떼어낸 첨부) */
    int deleteDraftAttachmentsNotIn(@Param("draftId") Long draftId,
                                    @Param("keepIds") List<Long> keepIds);

    // ---------- 삭제 ----------

    /** (5) 삭제 전 확보 — 이 초안에 묶인 첨부 전체의 물리 경로 */
    List<String> findAttachmentUrlsByDraftId(@Param("draftId") Long draftId);

    /** (5) 초안 물리 삭제 (attachments 는 fk_attachments_draft ON DELETE CASCADE 로 함께 정리) */
    int deleteDraft(@Param("draftId") Long draftId, @Param("userId") Long userId);

    // ---------- 발행 연동 ----------

    /**
     * (6) 발행: 초안 첨부의 소유권을 게시글로 이전. **초안 DELETE 보다 반드시 먼저** 실행.
     * post_id 세팅 + draft_id NULL → 단일 UPDATE 라 완화 CHECK 만족, CASCADE 대상에서 빠진다.
     */
    int bindDraftAttachmentsToPost(@Param("draftId") Long draftId, @Param("postId") Long postId);

    // ---------- 선업로드 (에디터 이미지 / 초안 첨부) ----------

    /** 대기 첨부 INSERT (post_id·draft_id 둘 다 NULL). 생성 attachment_id 는 holder.attachmentId 로 되받는다 */
    int insertPendingAttachment(@Param("a") PendingAttachment a);

    /** /files/{attachmentId} 서빙용 파일 정보 (state='normal' 만) */
    ServeFileInfo findServeFileInfo(@Param("attachmentId") Long attachmentId);

    // ---------- 청소 배치 ----------

    /** (7) 업로드 대기(둘 다 NULL) + N시간 경과 첨부의 물리 경로 (삭제 전 확보) */
    List<String> findExpiredPendingAttachmentUrls(@Param("hours") int hours);

    /** (7) 위 대상 물리 DELETE */
    int deleteExpiredPendingAttachments(@Param("hours") int hours);
}
