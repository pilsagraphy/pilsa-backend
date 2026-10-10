package com.back.guestbook.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 방명록 한 장 (guestbook_notes + 붙인 스티커).
 * ink: ink(검정 잉크) / gray(연한 잉크) / pencil(연필), paper: plain / cream / lined, tilt: 카드 기울기(도, 서버가 정함).
 * isMember: 로그인 상태로 실명을 남긴 글. isMine: 지금 로그인한 사람의 글(지울 수 있다). state 는 관리자 화면에만 의미가 있다.
 */
@Data
public class GuestbookNoteDto {
    private Long noteId;
    private String semesterLabel;
    private String displayName;
    private Long userId;
    private Boolean isMember;
    private Boolean isMine;
    private String content;
    private String ink;
    private String paper;
    private Integer tilt;
    private String state;
    private LocalDateTime createdAt;
    private List<StickerOnNote> stickers = new ArrayList<>();

    @Data
    public static class StickerOnNote {
        private Long noteId;
        private Long stickerId;
        private Integer slot;
        private String name;
        private String imageUrl;
    }
}
