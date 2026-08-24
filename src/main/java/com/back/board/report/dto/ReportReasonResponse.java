package com.back.board.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

// 신고 사유 카테고리 한 행 (reasons 테이블 노출 — FE 하드코딩 제거용)
@Getter
@Setter
@Schema(description = "신고 사유 카테고리 (신고 모달 셀렉트바)")
public class ReportReasonResponse {

    @Schema(description = "사유 id (신고 접수 시 reasonId 로 전달)", example = "1")
    private Long reasonId;

    @Schema(description = "사유 코드. code=ETC 일 때만 신고 detail 입력 필요", example = "ABUSE")
    private String code;

    @Schema(description = "화면 노출 라벨", example = "욕설/비방")
    private String label;

    @Schema(description = "노출 순서(오름차순)", example = "1")
    private Integer displayOrder;
}
