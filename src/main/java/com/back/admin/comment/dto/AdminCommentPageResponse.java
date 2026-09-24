package com.back.admin.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 댓글 관리 목록 페이지 응답
@Getter
@Setter
@Schema(description = "댓글 관리 목록 페이지 응답")
public class AdminCommentPageResponse {

    @Schema(description = "전체 페이지 수")
    private int totalPages;

    @Schema(description = "전체 댓글 수 (필터 적용 기준, deleted 제외)")
    private int totalCount;

    @Schema(description = "현재 페이지의 댓글 목록")
    private List<AdminCommentListResponse> comments;
}
