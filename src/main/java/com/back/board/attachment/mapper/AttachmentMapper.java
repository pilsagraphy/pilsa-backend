package com.back.board.attachment.mapper;

import com.back.board.attachment.dto.AttachmentFileRow;
import com.back.board.attachment.dto.PendingAttachmentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 선업로드 첨부 전용 매퍼.
 *
 * 글에 이미 연결된 첨부의 조회·삭제는 BoardMapper 가 계속 담당하고,
 * 여기는 "글이 없는 상태의 파일"(post_id IS NULL) 을 다루는 쿼리만 소유한다.
 */
@Mapper
public interface AttachmentMapper {

    /** 선업로드 저장 (post_id 는 NULL — 글은 아직 없다). 생성된 PK 는 row.attachmentId 에 채워진다 */
    void insertPending(AttachmentFileRow row);

    /** 파일 조회/다운로드 권한 판정용 1행 (연결된 글·게시판까지 조인) */
    AttachmentFileRow findFileRow(@Param("fileId") Long fileId);

    /**
     * 선업로드 파일을 글에 연결한다.
     * 내가 올린(uploader_id) · 아직 연결되지 않은(post_id IS NULL) 것만 연결되므로
     * 남의 파일 id 나 이미 쓰인 id 를 섞어 보내도 무해하다.
     */
    int linkToPost(@Param("postId") Long postId,
                   @Param("attachmentIds") List<Long> attachmentIds,
                   @Param("uploaderId") Long uploaderId);

    /** 본문에서 사라진 인라인 이미지 (수정 시 정리 대상) */
    List<PendingAttachmentRow> findUnreferencedInline(@Param("postId") Long postId,
                                                     @Param("keepIds") List<Long> keepIds);

    /** 첨부 소프트삭제 (id 목록) */
    int softDeleteByIds(@Param("attachmentIds") List<Long> attachmentIds);

    // ---- 임시저장(draft) 연동 — 초안 첨부는 발행 전 개인 작업물이라 물리 삭제한다 (drafts 와 같은 예외) ----

    /**
     * 선업로드 파일을 초안에 귀속한다.
     * 내가 올린(uploader_id) + 게시글 미연결(post_id IS NULL) + 대기중이거나 이미 이 초안(draft_id NULL or =draftId)만 —
     * 남의 대기 첨부 가로채기·발행글 첨부 탈취·다른 초안 첨부 강탈을 전부 차단한다.
     */
    int linkToDraft(@Param("draftId") Long draftId,
                    @Param("attachmentIds") List<Long> attachmentIds,
                    @Param("uploaderId") Long uploaderId);

    /** 이 초안에 묶였으나 keepIds 에 없는 첨부 (저장 시 재조정의 삭제 대상. keepIds 가 비면 전체) */
    List<PendingAttachmentRow> findDraftAttachmentsExcept(@Param("draftId") Long draftId,
                                                          @Param("keepIds") List<Long> keepIds);

    /** 이 초안의 모든 첨부 (초안 삭제 전에 행·물리파일을 지우기 위한 목록) */
    List<PendingAttachmentRow> findDraftAttachments(@Param("draftId") Long draftId);

    /** 초안 첨부 행 물리 삭제 (id 목록). 게시글에 연결된 행은 조건상 지워지지 않는다 */
    int deleteDraftAttachmentsByIds(@Param("draftId") Long draftId,
                                    @Param("attachmentIds") List<Long> attachmentIds);

    /**
     * 주어진 id 중 아직 존재하는 행의 id.
     * 가드된 DELETE(초안·정리 배치)가 스킵한 행(그 사이 발행·초안 귀속된 것)의 물리 파일을
     * 지우지 않기 위해, 삭제 후 생존 행을 확인해 차집합만 파일 삭제 대상으로 삼는 데 쓴다.
     */
    List<Long> findExistingIds(@Param("attachmentIds") List<Long> attachmentIds);

    /**
     * 발행 이관: 초안 첨부의 소유권을 게시글로 넘긴다 (draft_id 를 비워 CASCADE 대상에서 제외).
     * ⚠ 반드시 초안 DELETE 보다 먼저 호출 — 순서를 바꾸면 CASCADE 가 방금 발행한 글의 첨부를 지운다.
     */
    int transferDraftAttachmentsToPost(@Param("draftId") Long draftId,
                                       @Param("postId") Long postId,
                                       @Param("uploaderId") Long uploaderId);

    /** 보존기간이 지난 미연결 선업로드 파일 (정리 배치용 — 초안에 묶인 파일은 제외) */
    List<PendingAttachmentRow> findExpiredPending(@Param("hours") int hours);

    /** 미연결 선업로드 행 물리 삭제 (글에 속한 적이 없는 파일이라 증적 가치가 없다) */
    int deletePendingByIds(@Param("attachmentIds") List<Long> attachmentIds);

    /** 정책값 조회 (허용 확장자·보존시간) */
    String findPolicySetting(@Param("code") String code);
}
