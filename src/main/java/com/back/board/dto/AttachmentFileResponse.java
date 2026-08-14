package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

// 첨부파일 정보
@Getter
@Setter
public class AttachmentFileResponse {
    private Long attachmentId;
    private String originName;  // 사용자가 올릴 때 이름 (예: 보고서.pdf)
    private String fileUrl;     // 서버에 저장된 실제 경로
    private Long fileSize;      // 파일 크기 (byte 단위)
}
