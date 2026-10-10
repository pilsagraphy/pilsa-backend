package com.back.guestbook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 방명록 화면 한 번에 — 학기 목록(탭) · 지금 학기 · 스티커 서랍 · 고른 학기의 글 */
@Data
@AllArgsConstructor
public class GuestbookResponse {
    private String currentSemester;
    private String semester;
    private List<String> semesters;
    private int maxLength;
    private int maxStickers;
    private List<GuestbookStickerDto> stickers;
    private List<GuestbookNoteDto> notes;
}
