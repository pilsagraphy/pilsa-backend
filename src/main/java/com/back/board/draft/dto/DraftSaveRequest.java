package com.back.board.draft.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 임시저장 저장/덮어쓰기 요청 (POST/PUT 공통).
 *
 * 저장 시점에는 게시판 정책(익명 허용 등)을 검증하지 않는다 — 발행(POST /posts) 때만 검증한다.
 * title·content 둘 다 비어 있으면 "저장할 내용이 없습니다"(400).
 *
 * 리치 에디터 본문 이미지는 content(HTML) 안의 <img src="/files/{attachmentId}"> 로 이미 들어가 있고,
 * 일반 첨부파일(pdf 등)은 선업로드로 만들어 둔 대기 첨부의 id 를 attachmentIds 로 넘긴다.
 * 서버는 저장 트랜잭션에서 "본문이 참조하는 이미지 + attachmentIds" 만 이 초안에 묶고,
 * 이전에 묶여 있었으나 지금 참조가 끊긴 것은 물리 삭제한다(reconcile).
 */
@Getter
@Setter
public class DraftSaveRequest {

    @Schema(description = "제목 (작성 중이라 선택, 200자 이내)", example = "쓰다 만 제목", nullable = true)
    private String title;

    @Schema(description = "본문 HTML (작성 중이라 선택). 본문 이미지는 <img src=\"/files/{attachmentId}\"> 형태로 포함",
            example = "<p>쓰다 만 본문</p><img src=\"/files/12\">", nullable = true)
    private String content;

    @Schema(description = "카테고리 ID (선택)", example = "4", nullable = true)
    private Long categoryId;

    @Schema(description = "익명 여부 (발행 시점에 게시판 정책으로 최종 판정)", example = "false")
    private Boolean isAnonymous = false;

    @Schema(description = "이 초안에 묶을 일반 첨부(선업로드 대기 첨부)의 id 목록 (선택). 본문 이미지는 content 에서 자동 추출",
            example = "[31, 32]", nullable = true)
    private List<Long> attachmentIds;

    @Schema(description = "[서버 내부용] INSERT 후 생성된 draft_id 를 담는 자리. 요청 시 입력 불필요", hidden = true)
    private Long draftId;
}
