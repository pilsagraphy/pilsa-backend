package com.back.student.info.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 정보게시판 전체조회 페이지 정보
@Getter
@Setter
public class InfoPageResponse {
    private int totalPages;
    private List<InfoListResponse> posts;
}