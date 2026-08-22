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

    /** 보존기간이 지난 미연결 선업로드 파일 (정리 배치용) */
    List<PendingAttachmentRow> findExpiredPending(@Param("hours") int hours);

    /** 미연결 선업로드 행 물리 삭제 (글에 속한 적이 없는 파일이라 증적 가치가 없다) */
    int deletePendingByIds(@Param("attachmentIds") List<Long> attachmentIds);

    /** 정책값 조회 (허용 확장자·보존시간) */
    String findPolicySetting(@Param("code") String code);
}
