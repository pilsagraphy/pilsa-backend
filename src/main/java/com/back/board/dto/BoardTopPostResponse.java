package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 메인/사이드 화면용 상단 N개 글.
 * 개수는 프론트가 요청한 num 으로 정해진다 (예전에는 5건 고정이었다).
 */
@Getter
@Setter
public class BoardTopPostResponse {
    private Long postId;
    private String title;
    private Boolean isPinned;
}
