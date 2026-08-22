package com.back.board.draft.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 선업로드(pre-upload) 응답 — 본문 인라인 이미지 / 초안 첨부 공통.
 *
 * 이미지는 발행/임시저장 구분 없이 에디터에 넣는 순간 선업로드가 필수다(표시할 URL 이 즉시 필요, 백로그 A-4).
 * url 은 에디터가 마크다운 ![](url) 로 삽입하고, attachmentId 는 저장 시 attachmentIds 로 되돌려 보내
 * 서버가 이 첨부를 초안/게시글에 귀속시키는 데 쓴다.
 * url 에는 소유자(draft/post)가 들어가지 않아 발행돼도 깨지지 않는다(파일 이동 없음).
 */
@Getter
@Setter
public class AttachmentUploadResponse {

    private Long attachmentId; // 저장 시 attachmentIds 로 되돌려 보낼 값
    private String url;        // 정적 서빙 경로 (/uploads/...). 에디터 삽입/미리보기용
    private String originName; // 원본 파일명 (첨부목록 표시용). 이미지 업로드 시엔 참고용
    private Long fileSize;     // byte

    public AttachmentUploadResponse(Long attachmentId, String url, String originName, Long fileSize) {
        this.attachmentId = attachmentId;
        this.url = url;
        this.originName = originName;
        this.fileSize = fileSize;
    }
}
