package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 전체조회 목록의 게시글 한 건 정보 (공지/자유/정보 통합).
 * 게시판마다 의미 없는 필드는 기본값(0/false/null)으로 내려간다.
 *  - commentCount / categoryName : 공지사항은 미사용
 *  - isPinned                    : 공지사항 전용(그 외는 false)
 */
@Getter
@Setter
public class BoardListResponse {
    private Long postId;
    private String title;
    private String authorName;
    private int likeCount;
    private int viewCount;
    private int commentCount;
    private String categoryName;
    private Boolean isPinned;
    private boolean hasAttachment;
    private LocalDateTime created;
}
