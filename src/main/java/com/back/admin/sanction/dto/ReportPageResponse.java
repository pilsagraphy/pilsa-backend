package com.back.admin.sanction.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 신고 관리 목록 페이지 응답
@Getter
@Setter
public class ReportPageResponse {
    private int totalPages;
    private int totalCount;
    private List<ReportedItemResponse> items;
}
