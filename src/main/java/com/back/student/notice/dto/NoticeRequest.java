package com.back.student.notice.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// 공지사항 등록 요청 DTO
@Getter
@Setter
public class NoticeRequest {
    private String title;
    private String content;
    private boolean isPinned;

    private Long postId; // 등록 시에는 프론트가 안줘도 됨 DB 저장 후 서버 내부에서 사용

    // 프론트에서 보내는 실제 파일들
    private List<MultipartFile> files;

//    private List<Long> attachmentIds; // 첨부파일 ID 리스트
//    private List<Long> imageIds;      // 이미지 ID 리스트
}