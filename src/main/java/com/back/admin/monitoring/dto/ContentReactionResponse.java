package com.back.admin.monitoring.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 최근 N일 게시판별 콘텐츠 반응 — 글은 state='normal', 댓글은 state='normal' 이고 비밀 댓글이 아닌 것만 센다 */
@Data
public class ContentReactionResponse {
    private int days;
    private List<BoardRow> boards = new ArrayList<>();

    @Data
    public static class BoardRow {
        private Long boardId;
        private String boardName;
        private int postCount;
        private int commentCount;
        private int likeCount;
        private int zeroCommentPosts;
        /** 첫 댓글(글쓴이 본인 댓글 제외)까지 걸린 분의 중앙값. 댓글 달린 글이 없으면 null */
        private Integer medianFirstCommentMinutes;
    }

    /** 글 하나의 첫 댓글까지 걸린 분 — 서비스가 게시판별 중앙값으로 접는다 */
    @Data
    public static class FirstCommentRow {
        private Long boardId;
        private int minutes;
    }
}
