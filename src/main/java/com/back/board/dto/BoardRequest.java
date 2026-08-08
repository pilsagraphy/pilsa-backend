package com.back.board.dto;

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
    private String title;
    private String content;
    private boolean isAnonymous;   // 자유게시판 익명 여부
    private boolean isPinned;      // 공지사항 중요표시 여부
    private Long categoryId;       // 카테고리 (미선택 시 게시판별 기본값 적용)
    private List<MultipartFile> files;

    private Long postId;           // DB 저장 후 생성된 ID를 담기 위한 필드
}
