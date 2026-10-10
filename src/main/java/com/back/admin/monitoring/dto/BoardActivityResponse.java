package com.back.admin.monitoring.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 게시판 활동 — 기간 안 게시판별 글·댓글·좋아요 수와, 날짜별 전체 글·댓글 수(그래프용) */
@Data
public class BoardActivityResponse {
    private int days;
    private List<BoardRow> boards = new ArrayList<>();
    private List<DayRow> daily = new ArrayList<>();

    @Data
    public static class BoardRow {
        private Long boardId;
        private String boardName;
        private int postCount;
        private int commentCount;
        private int likeCount;
    }

    @Data
    public static class DayRow {
        private LocalDate date;
        private int posts;
        private int comments;
    }
}
