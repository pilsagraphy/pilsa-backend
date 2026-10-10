package com.back.guestbook.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 방명록 한 장 (guestbook_notes + 작성자가 그려 붙인 스티커 guestbook_drawings).
 * font: 손글씨체 키(pen/brush/gaegu/himelody/gamja/poor), ink: 잉크 색 키, paper: plain/lined/grid/cream, align: left/center/right,
 * tilt: 카드 기울기(도, 서버가 정함). isMine: 로그인한 본인 글(고치거나 지울 수 있다). state 는 관리자 화면에만 의미가 있다.
 */
@Data
public class GuestbookNoteDto {
    private Long noteId;
    private String semesterLabel;
    private String displayName;
    private Long userId;
    private Boolean isMine;
    /** 고치거나 지울 수 있는가 — 본인(로그인) 또는 관리자 */
    private Boolean canManage;
    private String content;
    private String font;
    private String ink;
    private String paper;
    private String align;
    private Integer tilt;
    private String state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Drawing> drawings = new ArrayList<>();

    /** 그려 붙인 스티커 — 위치·크기는 카드 폭·높이 기준 % (posX/posY 는 그림 중심), rotation 은 도 */
    @Data
    public static class Drawing {
        private Long drawingId;
        private Long noteId;
        private String imageUrl;
        private Double posX;
        private Double posY;
        private Double widthPct;
        private Integer rotation;
        /** 0.1~1 (투명도) */
        private Double opacity;
        private Integer sortOrder;
    }
}
