package com.back.mypage.activity.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 마이페이지 '내가 쓴 댓글' 목록 한 줄 (대댓글 포함, MyBatis 매핑)
@Getter
@Setter
public class MyCommentRow {
    private Long commentId;
    private Long postId;         // 원글 이동용
    private Long boardId;        // 원글 이동용 (프론트 라우팅)
    private String postTitle;    // 원글 제목
    private String content;      // 댓글 내용
    private LocalDateTime created;
}
