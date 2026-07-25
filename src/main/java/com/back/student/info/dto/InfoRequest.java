package com.back.student.info.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Getter
@Setter
public class InfoRequest {
    private String title;
    private String content;
    private Long categoryId;
    private List<MultipartFile> files;
    private Long postId;
}