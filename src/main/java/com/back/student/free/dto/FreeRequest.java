package com.back.student.free.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

// 게시글 등록
@Getter
@Setter
public class FreeRequest {
    private String title;
    private String content;
    private boolean isAnonymous;
    private Long categoryId;
    private List<MultipartFile> files;

    private Long postId; // DB 저장 후 ID 반환용
}