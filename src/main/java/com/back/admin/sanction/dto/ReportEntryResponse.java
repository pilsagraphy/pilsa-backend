package com.back.admin.sanction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 한 대상(게시글/댓글)에 들어온 '개별 신고' 1건.
 *
 * 신고 처리 모달의 '신고자 목록' 표를 채운다 — 대상 목록(ReportedItemResponse)이 대표 사유 1개로 접어
 * 보여주는 것과 달리, 신고자마다 다른 사유·상세를 개별 행으로 내려준다.
 * 신고자 이름을 담는다 — 운영진에게까지 숨기지 않기로 했다 (2026-09-20 PM). 일반 회원 화면에는 이 응답이 가지 않는다.
 *
 * 반려(rejected)된 신고는 내려주지 않는다 — 근거 없다고 판정된 신고를 현재 신고자 목록에 섞으면
 * 관리자가 옛 라운드의 무효 신고를 근거로 조치하게 된다. 남는 pending/resolved 는 status 로 구분한다.
 */
@Data
@Schema(description = "개별 신고 1건 (신고 처리 모달의 신고자 목록 행)")
public class ReportEntryResponse {

    @Schema(description = "신고 ID")
    private Long reportId;

    @Schema(description = "신고자 이름 — 운영진에게는 숨기지 않는다 (2026-09-20 PM 결정)", example = "김선하")
    private String reporterName;

    @Schema(description = "신고 사유 (reasons.label, 한글)", example = "스팸 · 홍보/도배")
    private String reasonLabel;

    @Schema(description = "상세 사유 — 신고자가 직접 적은 내용. '기타' 사유일 때만 값이 있고 그 외에는 null", example = "근거 없는 정보")
    private String detail;

    @Schema(description = "신고 접수 일시", example = "2026-09-04T17:19:13")
    private LocalDateTime createdAt;

    @Schema(description = "처리 상태 — pending(미처리) / resolved(삭제 조치로 종료). 반려(rejected)는 응답에 포함되지 않는다",
            example = "pending")
    private String status;
}
