package com.back.board.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 단일글 상세조회 (전 게시판 공통).
 *
 *  - isPinned    : 상단 고정 여부. 등록/수정 요청으로 직접 받지 않고,
 *                  선택한 카테고리가 '중요'(code=PINNED)인지로 서버가 결정한다.
 *  - isAnonymous : 익명 허용 게시판에서만 true 가능
 *  - prevPost/nextPost : 없으면 null (첫 글/마지막 글)
 */
@Getter
@Setter
public class BoardDetailResponse {
    private Long postId;
    private Long boardId;           // 프론트가 링크를 조합할 때 필요
    private String title;
    private String content;
    private Long userId;            // 작성자 ID (본인 확인용). 익명글은 마스킹되어 null
    private String authorName;
    private String categoryName;
    private Boolean isAnonymous;
    private Boolean isPinned;

    private int viewCount;
    private int likeCount;
    private Boolean isLiked;

    private LocalDateTime created;
    private LocalDateTime updated;

    private AdjacentPostResponse prevPost;
    private AdjacentPostResponse nextPost;

    private List<AttachmentFileResponse> attachments;
    private int attachmentCount;

    // 댓글 본문은 상세 응답에 싣지 않는다 — GET /api/user/boards/{boardId}/posts/{postId}/comments 로 따로 조회한다.
    // 목록/헤더에 "댓글 n"을 그리는 데는 개수만 있으면 되기 때문.
    private int commentCount;

    // 매퍼가 이전/다음 글 id만 먼저 채우고, 서비스가 이 id로 상세를 조회해 위 prevPost/nextPost 를 세팅한다.
    // 내부 전달용이라 응답 JSON에는 나가지 않는다.
    @JsonIgnore
    private Long prevPostId;
    @JsonIgnore
    private Long nextPostId;
}
