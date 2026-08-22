package com.back.board.draft.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 임시저장 생성(POST)·덮어쓰기(PUT) 공통 요청.
 *
 * 게시글 등록(BoardRequest)과 달리 title·content 에 @NotBlank 를 걸지 않는다 —
 * 작성 중인 초안이라 제목/본문이 비어 있어도 저장을 허용한다.
 * "둘 다 비면 400"은 서비스에서 판정한다(한쪽만 채워도 저장 가능).
 *
 * 게시판 정책(익명 허용·카테고리 유효성)은 저장 시점에 보정하지 않고 저장 당시 값을 그대로 보존한다 —
 * 초안 단계에서 게시판 정책이 바뀔 수 있어, 검증은 발행(POST /posts) 시점으로 미룬다(SPEC-A5 §1).
 */
@Getter
@Setter
public class DraftRequest {

    @Schema(description = "제목 (작성 중이라 선택. content 와 둘 다 비면 400)", example = "작성 중", nullable = true)
    private String title;

    @Schema(description = "본문 마크다운 (작성 중이라 선택. title 과 둘 다 비면 400)", example = "본문", nullable = true)
    private String content;

    @Schema(description = "카테고리 ID (선택). 저장 시엔 유효성 검증하지 않고 그대로 보존 — 검증은 발행 시점",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private Long categoryId;

    // Boolean 래퍼 사용 — primitive boolean + isXxx 조합은 폼 키 isAnonymous 가 바인딩되지 않는다(CLAUDE.md 규칙).
    @Schema(description = "익명 여부. 저장 시엔 보정 없이 그대로 보존", example = "false")
    private Boolean isAnonymous = false;

    /**
     * 이 초안이 현재 참조하는 첨부/본문이미지 attachment_id 목록.
     * 선업로드(POST .../posts/images, .../posts/attachments)로 만들어진 id 를 프론트가 그대로 보낸다.
     * 저장 시 이 집합으로 재조정(reconcile)한다: 목록의 첨부는 이 초안에 귀속시키고,
     * 이전엔 이 초안에 묶였으나 목록에서 빠진 첨부는 DB·물리파일까지 삭제한다.
     * (본문은 마크다운으로 확정 — HTML 파싱이 아니라 이 명시적 목록을 소유의 정본으로 삼는다.)
     */
    @Schema(description = "이 초안이 참조하는 첨부/이미지 attachment_id 목록 (선업로드 id). 저장 시 이 집합으로 소유를 재조정",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "[31, 32]")
    private List<Long> attachmentIds;

    @Schema(description = "[서버 내부용] DB 저장 후 생성된 임시저장 ID. 요청 시 입력 불필요", hidden = true)
    private Long draftId;

    // 제목·본문이 모두 비었는가 (양쪽 공백/누락이면 저장 거부)
    public boolean isEmpty() {
        return isBlank(title) && isBlank(content);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
