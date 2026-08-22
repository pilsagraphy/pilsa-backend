package com.back.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 게시글 등록 요청 (공지/자유/정보 통합).
 * 게시판에 따라 사용하는 필드가 다르다.
 *  - isAnonymous : 자유게시판 익명 여부
 *  - isPinned    : 공지사항 중요표시
 *  - categoryId  : 자유/정보게시판 카테고리 (공지는 미사용)
 */
@Getter
@Setter
public class BoardRequest {

    @Schema(description = "제목 (필수, 200자 이내)", example = "안녕하세요")
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
    private String title;

    @Schema(description = "내용 (필수)", example = "본문 내용입니다.")
    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    // Boolean 래퍼를 쓰는 이유: primitive boolean + 필드명 isXxx 조합은 자바 빈 규약상
    // 프로퍼티명이 "anonymous"가 되어 요청 폼 키 isAnonymous 가 바인딩되지 않고
    // 응답 JSON 필드명도 anonymous 로 나간다. 래퍼면 프로퍼티명이 isAnonymous 그대로 유지된다.
    @Schema(description = "익명 여부 (익명 허용 게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private Boolean isAnonymous = false;

    // isPinned 는 요청으로 받지 않는다 — 선택한 카테고리가 '중요'(code=PINNED)인지로 서버가 결정한다.
    // 카테고리 목록은 관리자에게만 '중요'를 포함해 내려가므로 일반 회원은 애초에 고를 수 없다.

    @Schema(description = "카테고리 ID (선택). 미입력하거나 없는 값이면 게시판별 기본값 자동 적용(자유=1, 정보=2). 공지사항은 미사용",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private Long categoryId;

    @Schema(description = "첨부파일 목록 (선택). 발행 시점에 함께 올리는 방식 — 선업로드(attachmentIds)와 병행 가능",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private List<MultipartFile> files;

    // 선업로드(POST .../files)로 이미 올린 파일을 이 글에 연결한다.
    // 본문에 남아 있는 /api/user/files/{id} 는 서버가 본문을 훑어 자동으로 함께 연결하므로,
    // 인라인 이미지 id 를 프론트가 빠뜨려도 고아가 되지 않는다 (AttachmentService.linkToPost)
    @Schema(description = "선업로드한 파일의 attachmentId 목록 (선택). 에디터에서 미리 올린 이미지·첨부를 이 글에 연결",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private List<Long> attachmentIds;

    @Schema(description = "[서버 내부용] DB 저장 후 생성된 게시글 ID. 요청 시 입력 불필요", hidden = true)
    private Long postId;
}
