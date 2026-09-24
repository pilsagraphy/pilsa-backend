package com.back.event.dto;

import lombok.Getter;
import lombok.Setter;

/** event_images 한 행 (MyBatis 매핑). 조회·다운로드·삭제가 함께 쓴다 */
@Getter
@Setter
public class EventImageRow {
    private Long imageId;
    private Long eventId;
    private String fileUrl;    // /uploads/events/{eventId}/... (FileStorageUtil 규칙)
    private String fileName;   // 원본 파일명
    private String fileType;   // MIME
    private long fileSize;
    private String state;      // normal / deleted
    private String eventState; // 소속 일정의 state — 삭제된 일정의 이미지는 내려주지 않는다
}
