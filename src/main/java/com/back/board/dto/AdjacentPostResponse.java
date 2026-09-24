package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 상세조회의 이전글/다음글 정보.
 * 시안 하단 내비게이션이 "카테고리 뱃지 + 제목 + 날짜"를 그리므로 id만으로는 부족하다.
 * 없으면(첫 글/마지막 글) 상위 응답에서 null 로 내려간다.
 */
@Getter
@Setter
public class AdjacentPostResponse {
    private Long postId;
    private String title;
    private String categoryName;
    private LocalDateTime created;
}
