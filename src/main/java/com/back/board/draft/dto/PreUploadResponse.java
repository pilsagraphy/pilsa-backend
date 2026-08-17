package com.back.board.draft.dto;

import lombok.Getter;

/**
 * 선업로드(리치 에디터 이미지 / 초안 첨부) 응답.
 *
 * url 은 소유자(draft/post)와 무관하게 **안정적인** `/files/{attachmentId}` 형식이다.
 * 발행되어 소유자가 draft→post 로 바뀌어도 이 URL 은 그대로라 본문에 박아 둔 이미지가 깨지지 않는다.
 * 프론트는 이미지면 이 url 을 <img src> 로 본문에 삽입하고, 일반 첨부면 attachmentId 를
 * 저장/발행 요청의 attachmentIds 에 넣는다.
 */
@Getter
public class PreUploadResponse {
    private final Long attachmentId;
    private final String url;            // /files/{attachmentId}
    private final String originName;
    private final Long fileSize;
    private final String attachmentType; // file / image

    public PreUploadResponse(Long attachmentId, String url, String originName, Long fileSize, String attachmentType) {
        this.attachmentId = attachmentId;
        this.url = url;
        this.originName = originName;
        this.fileSize = fileSize;
        this.attachmentType = attachmentType;
    }
}
