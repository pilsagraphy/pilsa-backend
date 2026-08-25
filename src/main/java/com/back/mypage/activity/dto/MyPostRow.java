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
    private LocalDateTime created;
}
