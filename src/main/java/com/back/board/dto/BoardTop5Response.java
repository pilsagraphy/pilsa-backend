package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

// 메인 화면용 상단 5개 조회. isPinned는 공지사항에서만 의미가 있다(그 외 게시판은 항상 false).
@Getter
@Setter
public class BoardTop5Response {
    private Long postId;
    private String title;
    private Boolean isPinned;
}
