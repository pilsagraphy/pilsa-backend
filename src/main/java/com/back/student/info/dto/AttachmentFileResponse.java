package com.back.student.info.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachmentFileResponse {
    private Long attachmentId;
    private String originName;  // 사용자가 올릴 때 이름
    private String fileUrl;     // 서버 내 실제 경로
    private Long fileSize;      // 파일 크기
}