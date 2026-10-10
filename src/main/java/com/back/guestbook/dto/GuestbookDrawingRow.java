package com.back.guestbook.dto;

import lombok.Data;

/** guestbook_drawings 한 행 (파일 경로까지 — 이미지 내려주기용. 화면 응답은 GuestbookNoteDto.Drawing) */
@Data
public class GuestbookDrawingRow {
    private Long drawingId;
    private Long noteId;
    private String fileUrl;
    private String fileType;
    private Double posX;
    private Double posY;
    private Double widthPct;
    private Integer rotation;
    private Integer sortOrder;
    private String state;
}
