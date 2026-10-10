package com.back.guestbook.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 방명록 남기기·고치기 본문. 로그인 상태면 user_id 가 붙어 나중에 고치거나 지울 수 있다 (이름은 늘 자유 입력).
 * drawings: 그려 붙인 스티커. 새 그림은 dataUrl(PNG data URL), 이미 있는 그림은 drawingId 로 — 고칠 때 목록에 없는 그림은 떼어진다.
 */
@Data
public class GuestbookNoteRequest {
    private String displayName;
    private String content;
    private String font;
    private String ink;
    private String paper;
    private String align;
    private List<DrawingInput> drawings = new ArrayList<>();

    @Data
    public static class DrawingInput {
        private Long drawingId;
        private String dataUrl;
        private Double posX;
        private Double posY;
        private Double widthPct;
        private Integer rotation;
        private Double opacity;
    }
}
