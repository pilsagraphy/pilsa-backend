package com.back.admin.post.dto;

import lombok.Getter;
import lombok.Setter;

// 관리자 상세의 첨부파일
@Getter
@Setter
public class AdminAttachmentResponse {
    private Long attachmentId;
    private String originName;
    private Long fileSize;
    private String fileUrl;
}
