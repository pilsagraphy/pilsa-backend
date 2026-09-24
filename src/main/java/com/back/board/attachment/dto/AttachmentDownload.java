package com.back.board.attachment.dto;

import java.io.File;

/**
 * 인증형 파일 조회 결과 (권한 검사를 통과한 뒤의 응답 재료).
 *
 * inline=true 면 브라우저에 바로 표시(본문 이미지), false 면 다운로드(첨부).
 */
public record AttachmentDownload(File file, String fileName, String contentType, long fileSize, boolean inline) {
}
