package com.back.admin.moderation.revision;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 본문 스냅샷 한 장 (관리자 상세의 '이전 본문') */
@Getter
@Setter
public class ContentRevisionResponse {
    private Long revisionId;
    private String targetType;   // post / comment
    private Long targetId;
    private String triggerType;  // report / edit / moderation
    private String title;        // 글이면 그때의 제목
    private String content;      // 그때의 본문
    private Long savedBy;
    private String savedByName;  // 수정이면 작성자, 신고면 신고자, 조치면 관리자. 자동 블라인드는 null
    private LocalDateTime createdAt;
}
