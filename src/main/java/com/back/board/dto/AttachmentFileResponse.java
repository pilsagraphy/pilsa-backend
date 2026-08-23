package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

// 첨부파일 정보
@Getter
@Setter
public class AttachmentFileResponse {
    private Long attachmentId;
    private String originName;  // 사용자가 올릴 때 이름 (예: 보고서.pdf)
    private String fileUrl;     // 인증형 조회 API 주소 (/api/user/files/{id}) — 정적 경로 아님, fetch+Authorization 으로 접근
    private Long fileSize;      // 파일 크기 (byte 단위)
}
