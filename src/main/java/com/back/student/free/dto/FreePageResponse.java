package com.back.student.free.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 전체조회 페이지 정보
@Getter
@Setter
public class FreePageResponse {
    private int totalPages;
    private List<FreeListResponse> posts;
}