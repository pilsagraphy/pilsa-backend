package com.back.board.draft.mapper;

import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListResponse;
import com.back.board.draft.dto.DraftRequest;
import com.back.board.dto.AttachmentFileResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 임시저장(drafts) 매퍼 — drafts 테이블 쿼리만 소유한다.
 * 첨부(attachments) 조작(선업로드 귀속·발행 이관·삭제)은 AttachmentMapper 소관 —
 * 선업로드 체계(uploader_id/usage_type)와 한 곳에서 관리해야 정리 배치·권한 판정이 어긋나지 않는다.
 *
 * 남의/없는/다른 게시판의 초안 접근은 전부 404(존재 노출 방지)이므로,
 * 조회·수정·삭제 쿼리는 항상 user_id(+board_id)를 WHERE 에 함께 걸어 "본인 것만" 처리한다.
 * 상한(5개)은 DB 가 uq_drafts_user_board_slot 로 물리 강제하고, 서비스는 빈 슬롯을 찾아 INSERT 한다.
 */
@Mapper
public interface DraftMapper {

    /** 내 임시저장 목록 (게시판별, 최근 저장순). 일반 첨부 개수(attachCnt)를 함께 계산 */
    List<DraftListResponse> findDrafts(@Param("userId") Long userId,
                                       @Param("boardId") Long boardId,
                                       @Param("limit") Integer limit);

    /** 단건 메타 (본인 + 해당 게시판인 것만. 아니면 null → 404). 첨부는 별도 조회 */
    DraftDetailResponse findDraftDetail(@Param("draftId") Long draftId,
                                        @Param("userId") Long userId,
                                        @Param("boardId") Long boardId);

    /** 초안의 일반 첨부(usage_type='attachment') 목록 — 단건 응답용. 본문 이미지는 content 에 있어 제외 */
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

    /**
     * 초안 행 잠금 조회 (SELECT ... FOR UPDATE). 삭제·발행 트랜잭션의 첫 단계 —
     * 저장(updateDraft)이 drafts 행 UPDATE 로 시작하므로, 삭제도 drafts 행 락부터 잡아야
     * 락 획득 순서가 일치해(drafts → attachments) 동시 저장·삭제 교차 시 데드락과
     * "삭제 직전 새로 귀속된 첨부가 CASCADE 로만 지워져 파일이 고아로 남는" 경합이 사라진다.
     */
    Long lockDraft(@Param("draftId") Long draftId,
                   @Param("userId") Long userId,
                   @Param("boardId") Long boardId);

    /**
     * 초안 물리 삭제 (본인 것만).
     * ⚠ 첨부는 이 쿼리 전에 AttachmentService.deleteDraftAttachments 로 행·물리파일을 먼저 지운다 —
     * FK CASCADE 는 백스톱일 뿐, CASCADE 에 맡기면 디스크 파일이 고아로 남는다.
     */
    int deleteDraft(@Param("draftId") Long draftId, @Param("userId") Long userId);

    /** 회원의 모든 초안 id (탈퇴 시 초안·첨부 정리용) */
    List<Long> findDraftIdsByUser(@Param("userId") Long userId);

    /** policy_settings 단건 (draft_max_count 등) */
    String findPolicySetting(@Param("code") String code);
}
