package com.back.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 게시글 수정 요청 (multipart/form-data).
 * 첨부를 함께 다루므로 등록과 동일하게 multipart 로 받는다.
 * title/content 는 등록과 동일하게 필수(빈 문자열로 덮어쓰기 방지), 첨부는 증분 방식(추가/삭제)이다.
 */
@Getter
@Setter
public class BoardUpdateRequest {

    @Schema(description = "제목", example = "수정된 제목")
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
    private String title;

    @Schema(description = "내용", example = "수정된 본문입니다.")
    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    // Boolean 래퍼 사용 이유는 BoardRequest 참고 (프로퍼티명 isXxx 유지)
    @Schema(description = "익명 여부 (익명 허용 게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private Boolean isAnonymous = false;

    // isPinned 는 요청으로 받지 않는다 — 카테고리('중요')로 서버가 결정. 등록(BoardRequest)과 동일 규칙
    // 중요 → 일반 카테고리로 바꾸면 상단 고정도 자동 해제된다

    @Schema(description = "카테고리 ID. '중요' 카테고리를 고르면 상단 고정됩니다(관리자만)")
    private Long categoryId;

    @Schema(description = "새로 추가할 첨부파일 (선택). 기존 첨부는 유지됩니다",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private List<MultipartFile> files;

    @Schema(description = "삭제할 기존 첨부의 attachmentId 목록 (선택). 화면에서 X 누른 것만 보냅니다",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private List<Long> deleteAttachmentIds;

    // 수정 화면에서 새로 선업로드한 파일. 등록(BoardRequest)과 같은 규칙이며
    // 본문에서 지워진 인라인 이미지는 저장 시점에 정리된다 (AttachmentService.syncInlineAttachments)
    @Schema(description = "수정 중 새로 선업로드한 파일의 attachmentId 목록 (선택)",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private List<Long> attachmentIds;
}
