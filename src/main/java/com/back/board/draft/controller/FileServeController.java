package com.back.board.draft.controller;

import com.back.board.draft.dto.ServeFileInfo;
import com.back.board.draft.service.DraftService;
import com.back.board.exception.BoardException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 첨부/본문이미지를 attachment_id 로 스트리밍하는 안정 URL 엔드포인트.
 *
 * 물리 경로(uploads/board-{id}/…)에는 소유자(post_id)가 섞일 수 있어 발행 전후로 흔들리지만,
 * /files/{attachmentId} 는 소유권이 draft→post 로 바뀌어도 변하지 않는다 — 그래서 본문 이미지 URL 로 쓴다.
 * 리치 에디터 이미지의 재조정(reconcile)도 이 URL 에서 attachment_id 를 되뽑아 동작한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "파일 서빙", description = "attachment_id 로 첨부/이미지 스트리밍 (/files/{attachmentId})")
public class FileServeController {

    private final DraftService draftService;

    @Operation(summary = "파일 스트리밍", description = "attachment_id 로 첨부/본문이미지를 스트리밍합니다. 소유권이 바뀌어도 URL 이 안정적입니다.")
    @GetMapping("/files/{attachmentId}")
    public ResponseEntity<Resource> serve(@PathVariable Long attachmentId) throws IOException {
        ServeFileInfo info = draftService.getServeInfo(attachmentId);

        // file_url 은 DB 값이지만 경로 이탈 방어를 한 번 더 건다 (uploads 밖은 서빙 금지)
        String basePath = new File("").getAbsolutePath();
        File uploadsBase = new File(basePath, "uploads").getCanonicalFile();
        File target = new File(basePath, info.getFileUrl()).getCanonicalFile();
        if (!target.getPath().startsWith(uploadsBase.getPath()) || !target.exists() || !target.isFile()) {
            log.warn("파일 서빙 대상 없음/경로 이탈 - attachmentId: {}, fileUrl: {}", attachmentId, info.getFileUrl());
            throw new BoardException("존재하지 않는 파일입니다.", HttpStatus.NOT_FOUND);
        }

        MediaType mediaType = info.getFileType() != null
                ? safeMediaType(info.getFileType())
                : MediaType.APPLICATION_OCTET_STREAM;

        // 이미지 등은 인라인 표시, 그 외도 원본 파일명이 그대로 떨어지게 filename* 지정
        String fileName = info.getOriginName() != null ? info.getOriginName() : ("file-" + attachmentId);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = "inline; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(new FileSystemResource(target));
    }

    private MediaType safeMediaType(String type) {
        try {
            return MediaType.parseMediaType(type);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
