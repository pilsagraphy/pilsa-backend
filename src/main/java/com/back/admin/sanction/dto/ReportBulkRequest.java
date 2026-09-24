package com.back.admin.sanction.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 선택 복원 / 선택 삭제 / 선택 블라인드 (일괄). targetType 은 현재 활성 탭('post'/'comment').
// 단건 조치도 targetIds 에 1건만 담아 같은 API로 처리한다 (단건 전용 엔드포인트 없음).
// reasonId·detail 은 삭제/블라인드에서만 쓰이고, 복원은 사유를 받지 않는다.
@Getter
@Setter
public class ReportBulkRequest {
    private String targetType;
    private List<Long> targetIds;
    private Long reasonId;
    private String detail;
}
