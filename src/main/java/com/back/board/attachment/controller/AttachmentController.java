package com.back.board.attachment.controller;

import com.back.board.attachment.dto.AttachmentDownload;
import com.back.board.attachment.dto.AttachmentUploadResponse;
import com.back.board.attachment.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 파일 업로드/조회 (리치 에디터).
 *
 * 업로드가 게시판 하위 경로인 이유: 업로드 시점에 write_level·첨부 허용 여부를 검사해야
 * 글을 쓸 수도 없는 사람이 서버에 파일만 쌓는 것을 막을 수 있다.
 * 조회가 게시판 밖 경로인 이유: 본문 마크다운에 심긴 주소는 게시판이 바뀌어도 변하지 않아야 하며,
 * 열람 권한은 파일 → 글 → 게시판을 조인해 판정하므로 경로에 boardId 가 필요 없다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "게시판(글·댓글)", description = "게시판 통합 API. boardId 로 게시판 구분(기본: 1=공지사항, 2=자유게시판, 3=정보게시판). 관리자가 추가한 게시판도 동일 경로 사용")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "파일 업로드 (본문 이미지·첨부 공용, 선업로드)",
            description = """
                    에디터에서 **파일을 고른 즉시** 호출합니다(글은 아직 없는 상태). 깃허브 에디터와 같은 흐름입니다.

                    ### 프론트 구현 (깃허브와 동일한 UX)
                    ```js
                    // 1) 붙여넣기/드래그/파일선택 즉시 자리표시자를 본문에 삽입
                    const placeholder = `![Uploading ${file.name}…]()`;
                    insertAtCursor(placeholder);

                    // 2) 업로드
                    const form = new FormData();
                    form.append('file', file);
                    // usage 는 생략 가능 — 이미지는 inline, 그 외는 attachment 로 서버가 판단
                    const res = await fetch(`/api/user/boards/${boardId}/files`, {
                      method: 'POST', body: form, headers: { Authorization: `Bearer ${token}` }
                    });
                    const data = await res.json();

                    // 3) 자리표시자를 응답의 markdown 으로 교체 (실패하면 자리표시자를 지운다)
                    replaceText(placeholder, data.markdown);
                    ```
                    응답의 `markdown` 은 그대로 본문에 넣으면 되는 완성된 문자열입니다
                    (이미지면 `![파일명](url)`, 그 외는 `[파일명](url)` — 파일명 속 대괄호는 서버가 이스케이프).

                    ### 요청 (multipart/form-data)
                    ```
                    file  : 파일 1개                (필수)
                    usage : inline | attachment     (선택)
                            inline     = 본문에 삽입할 이미지 → 상세의 첨부 목록에는 나오지 않음
                            attachment = 첨부 목록에 노출할 파일
                            생략 시 이미지는 inline, 그 외는 attachment
                    ```

                    ### 응답 예시
                    ```json
                    {"attachmentId": 31, "url": "/api/user/files/31", "originName": "스크린샷.png",
                     "fileSize": 12345, "isImage": true, "usageType": "inline",
                     "markdown": "![스크린샷.png](/api/user/files/31)"}
                    ```

                    ### 업로드한 파일은 언제 글의 것이 되는가
                    발행(`POST .../posts`)·수정(`PUT .../posts/{postId}`) 요청의 **attachmentIds** 에 넣으면 그 글에 연결됩니다.
                    **임시저장(`POST/PUT .../drafts`)의 attachmentIds 에 넣으면 초안에 귀속**되어 발행 시 글로 이관됩니다.
                    본문에 `/api/user/files/{id}` 가 남아 있으면 attachmentIds 에 빠뜨려도 서버가 본문을 훑어 함께 연결·귀속하므로,
                    인라인 이미지는 프론트가 따로 목록을 관리하지 않아도 됩니다.
                    **글이나 초안 어디에도 연결되지 않은 파일만 24시간 뒤 새벽 배치가 삭제합니다**
                    (policy_settings.pending_upload_purge_hours). 초안에 귀속된 파일은 보존시간과 무관하게 유지되며,
                    초안을 삭제하면 그 파일도 함께 삭제됩니다.

                    실패: 400 {"message":"허용되지 않는 파일 형식입니다."} (policy_settings.upload_image_extensions / upload_file_extensions)
                    실패: 400 {"message":"업로드할 파일이 없습니다."}
                    실패: 400 {"message":"본문에 삽입할 수 있는 것은 이미지 파일뿐입니다."} (usage=inline + 비이미지)
                    실패: 400 {"message":"usage 는 inline 또는 attachment 만 사용할 수 있습니다."} (usage 에 그 외 값)
                    실패: 403 {"message":"이 게시판에 글을 등록할 권한이 없습니다."}
                    실패: 403 {"message":"이 게시판은 파일 업로드를 사용하지 않습니다."}
                    실패: 404 {"message":"존재하지 않는 게시판입니다. (boardId: N)"}
                    실패: 413 요청 크기 초과 (spring.servlet.multipart 한도 30MB)
                    """)
    @PostMapping(value = "/api/user/boards/{boardId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentUploadResponse> upload(
            @Parameter(description = "게시판 ID (기본 1=공지사항, 2=자유게시판, 3=정보게시판)", example = "2")
            @PathVariable Long boardId,
            @Parameter(description = "업로드할 파일 1개") @RequestParam("file") MultipartFile file,
            @Parameter(description = "용도: inline(본문 삽입 이미지) / attachment(첨부 목록). 생략 시 서버가 판단", example = "inline")
            @RequestParam(value = "usage", required = false) String usage) {
        log.info("파일 업로드 요청 - boardId: {}, usage: {}", boardId, usage);
        return ResponseEntity.ok(attachmentService.upload(boardId, file, usage));
    }

    @Operation(summary = "파일 조회/다운로드 (인증형)",
            description = """
                    업로드 응답의 `url`(= 본문 마크다운에 심긴 주소)이 가리키는 API입니다.
                    **열람 권한을 검사하는 유일한 파일 접근 경로**입니다(첨부 정적 서빙 /uploads/board-* 는 폐지됨) —
                    파일 → 글 → 게시판을 타고
                    boards.read_scope 를 판정하므로, 열람 권한이 없는 게시판의 첨부는 URL을 알아도 열리지 않습니다.

                    | 대상 | 열람 가능한 사람 |
                    |---|---|
                    | 글에 연결된 파일 | 그 게시판을 열람할 수 있는 회원 (관리자 포함) |
                    | 아직 글에 연결되지 않은 파일 (선업로드 대기·**임시저장 귀속** 포함) | 올린 본인만 (작성 중 미리보기) |
                    | 블라인드·삭제된 글의 첨부 | 관리자만 |

                    이미지는 `Content-Disposition: inline`(브라우저에 바로 표시), 그 외 파일은 `attachment`(원본 파일명으로 다운로드)로 내려갑니다.

                    ### 프론트: 본문 이미지 렌더링
                    `img` 태그는 Authorization 헤더를 붙일 수 없으므로, 마크다운을 그릴 때 이미지 컴포넌트를 갈아끼워
                    한 번 fetch 한 뒤 blob URL 로 표시합니다(첨부 다운로드도 같은 방식).
                    ```js
                    const res  = await fetch(src, { headers: { Authorization: `Bearer ${token}` } });
                    const blob = await res.blob();
                    setUrl(URL.createObjectURL(blob));   // 언마운트 시 revokeObjectURL
                    ```

                    실패: 404 {"message":"존재하지 않는 파일입니다."}
                    (없는 파일 · 삭제된 첨부 · 열람 권한 없는 게시판의 첨부 · 블라인드/삭제된 글의 첨부 · 남의 선업로드 파일 —
                    **어떤 파일이 존재하는지 자체를 알려주지 않기 위해 전부 404 로 통일**)
                    """)
    @GetMapping("/api/user/files/{fileId}")
    public ResponseEntity<Resource> download(
            @Parameter(description = "첨부 ID (업로드 응답의 attachmentId)", example = "31")
            @PathVariable Long fileId) {
        AttachmentDownload download = attachmentService.download(fileId);

        ContentDisposition disposition = (download.inline()
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                // 한글 파일명이 깨지지 않게 RFC 5987(filename*) 형식으로 내린다
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // 저장된 MIME 을 브라우저가 임의로 다시 추측하지 못하게 한다 (업로드 파일 XSS 방어)
                .header("X-Content-Type-Options", "nosniff")
                // 인증이 필요한 리소스이므로 공용 캐시에는 담기지 않게 private
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(download.file()));
    }
}
