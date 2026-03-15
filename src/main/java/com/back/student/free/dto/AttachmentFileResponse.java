package com.back.student.free.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
public class AttachmentFileResponse {
    private Long attachmentId;
    private String originName;  // 사용자가 올릴 때 이름 (예: 보고서.pdf)
    private String fileUrl;     // 서버 하드디스크에 저장된 실제 경로 (예: /uploads/free/uuid.pdf)
    private Long fileSize;      // 파일 크기 (byte 단위)
}