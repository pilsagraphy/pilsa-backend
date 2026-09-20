package com.back.mypage.activity.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 마이페이지 '내가 쓴 글' / '좋아요 누른 글' 목록 한 줄 (MyBatis 매핑)
@Getter
@Setter
public class MyPostRow {
    private Long postId;
    private Long boardId;        // 원글 이동용 (프론트 라우팅)
    private String boardName;    // 게시판 한글명 (내가 쓴 글에서 노출)
    private String title;
    private int likeCount;
    private int viewCount;
    private int commentCount;   // 살아있는 댓글 수 (목록의 댓글 아이콘)
    private Boolean hasAttachment; // 첨부 목록용 파일이 있는가 (제목 옆 클립 아이콘) — 게시판 목록과 같은 정의
    private String authorName;  // 글쓴이 — 익명 글은 '익명' (게시판 목록과 같은 규칙)
    private String categoryName;  // 글의 카테고리 (없는 게시판이면 null)
    private LocalDateTime created;
}
