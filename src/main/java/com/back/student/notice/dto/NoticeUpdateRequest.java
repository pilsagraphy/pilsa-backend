package com.back.student.notice.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

// 공지사항 수정 요청 DTO
@Getter
@Setter
public class NoticeUpdateRequest {
    private String title;
    private String content;
    private boolean isPinned;
    private List<Long> attachmentIds;
    private List<Long> imageIds;
}