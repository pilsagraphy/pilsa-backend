package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 전체조회 페이지 정보 (전체 페이지 수 + 현재 페이지 글 목록)
@Getter
@Setter
public class BoardPageResponse {
    private int totalPages;
    private List<BoardListResponse> posts;
}
