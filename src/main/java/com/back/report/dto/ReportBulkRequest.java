package com.back.report.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 선택 반려 / 선택 삭제 (일괄). targetType 은 현재 활성 탭('post'/'comment').
@Getter
@Setter
public class ReportBulkRequest {
    private String targetType;
    private List<Long> targetIds;
}
