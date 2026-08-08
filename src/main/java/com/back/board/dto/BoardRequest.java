package com.back.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "제목", example = "안녕하세요")
    private String title;

    @Schema(description = "내용", example = "본문 내용입니다.")
    private String content;

    @Schema(description = "익명 여부 (자유게시판 전용). 그 외 게시판은 무시됨", example = "false")
    private boolean isAnonymous;

    @Schema(description = "중요표시 여부 (공지사항 전용). 그 외 게시판은 무시됨", example = "false")
    private boolean isPinned;

    @Schema(description = "카테고리 ID (자유/정보게시판). 미입력 시 게시판별 기본값 적용, 공지사항은 미사용")
    private Long categoryId;

    @Schema(description = "첨부파일 목록 (선택)")
    private List<MultipartFile> files;

    @Schema(description = "[서버 내부용] DB 저장 후 생성된 게시글 ID. 요청 시 입력 불필요", hidden = true)
    private Long postId;
}
