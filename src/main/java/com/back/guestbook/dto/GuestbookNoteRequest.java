package com.back.guestbook.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 방명록 남기기 본문. asMember=true 면(로그인 상태에서만) 회원 이름으로 남기고 user_id 가 붙는다 — 그 외엔 displayName(닉네임).
 * stickerIds 는 붙일 스티커(순서가 자리가 된다, 최대 guestbook_max_stickers).
 */
@Data
public class GuestbookNoteRequest {
    private String displayName;
    private Boolean asMember;
    private String content;
    private String ink;
    private String paper;
    private List<Long> stickerIds = new ArrayList<>();
}
