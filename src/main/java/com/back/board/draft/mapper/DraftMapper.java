package com.back.board.draft.mapper;

import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListResponse;
import com.back.board.draft.dto.DraftRequest;
import com.back.board.draft.dto.PendingAttachment;
import com.back.board.dto.AttachmentFileResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 임시저장(drafts) + 초안/선업로드 첨부(attachments) 매퍼.
 *
 * 남의/없는/다른 게시판의 초안 접근은 전부 404(존재 노출 방지)이므로,
 * 조회·수정·삭제 쿼리는 항상 user_id(+board_id)를 WHERE 에 함께 걸어 "본인 것만" 처리한다.
 * 상한(5개)은 DB 가 uq_drafts_user_board_slot 로 물리 강제하고, 서비스는 빈 슬롯을 찾아 INSERT 한다.
 */
@Mapper
public interface DraftMapper {

    // ---- 초안 CRUD ----

    /** 내 임시저장 목록 (게시판별, 최근 저장순). 일반 첨부 개수(attachCnt)를 함께 계산 */
    List<DraftListResponse> findDrafts(@Param("userId") Long userId,
                                       @Param("boardId") Long boardId,
                                       @Param("limit") Integer limit);

    /** 단건 메타 (본인 + 해당 게시판인 것만. 아니면 null → 404). 첨부는 별도 조회 */
    DraftDetailResponse findDraftDetail(@Param("draftId") Long draftId,
                                        @Param("userId") Long userId,
                                        @Param("boardId") Long boardId);

    /** 초안의 일반 첨부(attachment_type='file') 목록 — 단건 응답용. 본문 이미지는 제외 */
    List<AttachmentFileResponse> findDraftFileAttachments(@Param("draftId") Long draftId);

    /** 이 게시판에서 이 회원이 이미 쓰고 있는 슬롯 번호들 (빈 슬롯 탐색용) */
    List<Integer> findUsedSlots(@Param("userId") Long userId, @Param("boardId") Long boardId);

    /** 신규 저장. slot_no 는 서비스가 찾은 빈 슬롯. 생성된 draft_id 는 request.draftId 로 되돌아온다(useGeneratedKeys) */
    void insertDraft(@Param("userId") Long userId,
                     @Param("boardId") Long boardId,
                     @Param("slotNo") int slotNo,
                     @Param("request") DraftRequest request);

    /** 덮어쓰기. 본인 + 해당 게시판인 것만 갱신되고 슬롯은 유지. 0이면 없음/남의 것 → 404 */
    int updateDraft(@Param("draftId") Long draftId,
                    @Param("userId") Long userId,
                    @Param("boardId") Long boardId,
                    @Param("request") DraftRequest request);

    /** 초안 물리 삭제 (본인 것만). 연결된 첨부는 FK CASCADE 로 함께 삭제된다 */
    int deleteDraft(@Param("draftId") Long draftId, @Param("userId") Long userId);

    // ---- 첨부 (선업로드 / 재조정 / 발행 이관 / 삭제 전 파일경로 확보) ----

    /**
     * 선업로드 첨부 등록 (업로드 대기 상태: post_id·draft_id 둘 다 NULL — 완화 CHECK 로 허용).
     * 생성된 attachment_id 는 useGeneratedKeys 로 attachment.attachmentId 에 채워진다.
     */
    void insertPendingAttachment(PendingAttachment attachment);

    /**
     * 저장 시 재조정 ①: attachmentIds 를 이 초안에 귀속.
     * 내가 올린(uploaded_by) + 아직 게시글에 안 묶인(post_id NULL) + 대기중이거나 이미 이 초안(draft_id NULL or =draftId)인 것만.
     * (남의 대기 첨부를 가로채거나 발행된 글의 첨부를 훔쳐오지 못하게 하는 방어)
     */
    int linkAttachmentsToDraft(@Param("draftId") Long draftId,
                               @Param("userId") Long userId,
                               @Param("attachmentIds") List<Long> attachmentIds);

    /** 재조정 ②-a: 이 초안에 묶여 있으나 이번 목록에 없는 첨부의 물리 경로 (행 삭제 전에 확보) */
    List<String> findDraftAttachmentUrlsExcept(@Param("draftId") Long draftId,
                                               @Param("keepIds") List<Long> keepIds);

    /** 재조정 ②-b: 이 초안에 묶여 있으나 이번 목록에 없는 첨부 행 삭제 (본문에서 빠진 이미지/제거된 첨부) */
    int deleteDraftAttachmentsExcept(@Param("draftId") Long draftId,
                                     @Param("keepIds") List<Long> keepIds);

    /** 초안 삭제/발행 전, 이 초안의 모든 첨부 물리 경로 확보 (CASCADE 로 행이 사라지기 전에 URL 을 모아둔다) */
    List<String> findDraftAttachmentUrls(@Param("draftId") Long draftId);

    /**
     * 발행 이관: 초안 첨부의 소유권을 게시글로 넘긴다 (draft_id 를 비워 CASCADE 대상에서 제외).
     * ⚠ 반드시 초안 DELETE 보다 **먼저** 호출 — 순서를 바꾸면 CASCADE 가 방금 발행한 글의 첨부를 지운다.
     */
    int transferDraftAttachmentsToPost(@Param("draftId") Long draftId,
                                       @Param("userId") Long userId,
                                       @Param("postId") Long postId);

    // ---- 업로드 대기(고아) 첨부 청소 배치 ----

    /** post_id·draft_id 둘 다 NULL 인 채 cutoffHours 시간 이상 방치된 첨부의 물리 경로 */
    List<String> findOrphanAttachmentUrls(@Param("cutoffHours") int cutoffHours);

    /** 위 대상 행 물리 삭제 (같은 조건). 반환값은 삭제된 행 수 */
    int deleteOrphanAttachments(@Param("cutoffHours") int cutoffHours);

    // ---- 정책값 ----

    /** policy_settings 단건 (draft_max_count, draft_orphan_purge_hours 등) */
    String findPolicySetting(@Param("code") String code);
}
