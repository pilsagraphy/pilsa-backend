package com.back.student.notice.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AttachmentFileResponse {
    private Long attachmentId;
    private String originName;  // 사용자가 올릴 때 이름 (예: 보고서.pdf)
    private String fileUrl;  // 서버 하드디스크에 저장된 실제 경로 (예: C:/upload/uuid_보고서.pdf)
    private Long fileSize;      // 파일 크기 (byte 단위)
}