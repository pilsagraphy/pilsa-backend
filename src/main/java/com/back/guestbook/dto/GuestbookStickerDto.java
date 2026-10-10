package com.back.guestbook.dto;

import lombok.Data;

/** 관리자가 등록한 스티커 (guestbook_stickers). imageUrl 은 공개 경로 /api/guestbook/stickers/{id}/image */
@Data
public class GuestbookStickerDto {
    private Long stickerId;
    private String name;
    private String fileUrl;
    private String fileType;
    private String imageUrl;
    private Integer sortOrder;
}
