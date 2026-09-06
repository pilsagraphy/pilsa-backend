package com.back.admin.sanction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 한 대상(게시글/댓글)에 들어온 '개별 신고' 1건.
 *
 * 신고 처리 모달의 '신고자 목록' 표를 채운다 — 대상 목록(ReportedItemResponse)이 대표 사유 1개로 접어
 * 보여주는 것과 달리, 신고자마다 다른 사유·상세를 개별 행으로 내려준다.
 * 신고자 회원 ID·이름은 담지 않는다("신고자 정보는 공개되지 않습니다" 정책 — 응답에 실리면 개발자도구로 노출되므로).
 * 프론트가 createdAt 오름차순 그대로 익명A·익명B… 를 붙인다.
 *
 * 반려(rejected)된 신고는 내려주지 않는다 — 근거 없다고 판정된 신고를 현재 신고자 목록에 섞으면
 * 관리자가 옛 라운드의 무효 신고를 근거로 조치하게 된다. 남는 pending/resolved 는 status 로 구분한다.
 */
@Data
@Schema(description = "개별 신고 1건 (신고 처리 모달의 신고자 목록 행)")
public class ReportEntryResponse {

    @Schema(description = "신고 ID")
    private Long reportId;

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
