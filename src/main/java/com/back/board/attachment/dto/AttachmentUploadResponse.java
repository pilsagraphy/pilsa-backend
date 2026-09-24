package com.back.board.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 선업로드 응답 (POST /api/user/boards/{boardId}/files).
 *
 * 에디터가 파일을 고른 "그 순간" 받는 응답이다. 글(posts 행)은 아직 없다.
 *  - url      : 본문 마크다운에 심을 영구 주소. 물리 경로가 아니라 인증형 조회 API 경로다
 *  - markdown : 프론트가 그대로 본문에 붙여 넣으면 되는 완성된 마크다운 (이미지면 ![], 그 외는 [])
 *  - isImage  : 본문 삽입 대상인지(true) 첨부 목록 대상인지(false) 판단용
 */
@Getter
public class AttachmentUploadResponse {

    @Schema(description = "첨부 ID. 글 저장 시 attachmentIds 로 연결한다", example = "31")
    private final Long attachmentId;

    @Schema(description = "파일 주소 (본문 마크다운에 심는 값). 인증 필요", example = "/api/user/files/31")
    private final String url;

    @Schema(description = "업로드한 원본 파일명", example = "자료.pdf")
    private final String originName;

    @Schema(description = "파일 크기(byte)", example = "12345")
    private final Long fileSize;

    // Boolean 래퍼: primitive boolean + isXxx 조합은 JSON 필드명이 image 로 나간다 (CLAUDE.md 주의사항)
    @Schema(description = "이미지 여부. true 면 본문 삽입용(inline), false 면 첨부 목록용", example = "true")
    private final Boolean isImage;

    @Schema(description = "용도: inline(본문 삽입 이미지) / attachment(첨부 목록)", example = "inline")
    private final String usageType;

    @Schema(description = "본문에 그대로 붙여 넣을 마크다운", example = "![스크린샷.png](/api/user/files/31)")
    private final String markdown;

    public AttachmentUploadResponse(Long attachmentId, String originName, Long fileSize,
                                    boolean isImage, String usageType) {
        this.attachmentId = attachmentId;
        this.url = "/api/user/files/" + attachmentId;
        this.originName = originName;
        this.fileSize = fileSize;
        this.isImage = isImage;
        this.usageType = usageType;
        this.markdown = (isImage ? "!" : "") + "[" + escapeLinkText(originName) + "](" + this.url + ")";
    }

    /** 파일명에 대괄호가 있으면 마크다운 링크 문법이 깨지므로 이스케이프한다 */
    private static String escapeLinkText(String name) {
        return name == null ? "" : name.replace("[", "\\[").replace("]", "\\]");
    }
}
