package com.back.board.draft.dto;

import lombok.Getter;
import lombok.Setter;

/** /files/{attachmentId} 스트리밍 서빙에 필요한 최소 정보. */
@Getter
@Setter
public class ServeFileInfo {
    private String fileUrl;     // 물리 경로 (/uploads/...)
    private String fileType;    // MIME (image/png 등) — Content-Type 헤더용
    private String originName;  // 원본 파일명 — Content-Disposition 파일명용
}
