package com.back.board.draft.controller;

import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListResponse;
import com.back.board.draft.dto.DraftResponse;
import com.back.board.draft.dto.DraftSaveRequest;
import com.back.board.draft.dto.DraftSaveResponse;
import com.back.board.draft.dto.PreUploadResponse;
import com.back.board.draft.service.DraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 게시판 글쓰기 임시저장(draft) + 리치 에디터 선업로드.
 *
 * 경로는 게시판 하위(/api/user/boards/{boardId}/drafts)에 둔다 — 임시저장은 게시판 글쓰기의 일부다.
 * 회원당 최대 5개 슬롯. 남의/없는 draftId 는 전부 404(존재 여부 노출 방지).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/boards/{boardId}")
@Tag(name = "게시판 임시저장(글쓰기 초안)", description = "글쓰기 임시저장 CRUD + 리치 에디터 이미지/첨부 선업로드. 회원당 최대 5슬롯")
public class DraftController {

    private static final String BOARD_ID_DESC = "게시판 ID (기본 1=공지사항, 2=자유게시판, 3=정보게시판)";

    private final DraftService draftService;

    @Operation(summary = "임시저장 저장(신규)",
            description = """
                    글쓰기 화면의 '글 저장하기'를 처음 누를 때 호출합니다. 빈 슬롯(1~5)에 저장되고 draftId 를 돌려줍니다.
                    프론트는 이 draftId 를 보관했다가 다음 저장부터는 PUT(덮어쓰기)로 보냅니다.
                    본문 이미지는 content(HTML) 안의 <img src="/files/{id}"> 로 이미 들어 있어 자동 반영되고,
                    일반 첨부는 선업로드로 만든 대기 첨부 id 를 attachmentIds 로 넘깁니다.

                    실패: 400 {"message":"저장할 내용이 없습니다."} (제목·본문 모두 빈 경우)
                    실패: 403 {"message":"이 게시판에 글을 등록할 권한이 없습니다."}
                    실패: 409 {"message":"임시저장은 최대 5개까지 가능합니다. 기존 임시저장을 삭제해 주세요."}
                    """)
    @PostMapping("/drafts")
    public ResponseEntity<DraftSaveResponse> save(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @RequestBody DraftSaveRequest request) {
        log.info("임시저장 신규 - boardId: {}", boardId);
        return ResponseEntity.ok(draftService.save(boardId, request));
    }

    @Operation(summary = "임시저장 덮어쓰기",
            description = """
                    같은 슬롯의 초안을 이어 쓰다가 다시 저장할 때 호출합니다(슬롯 유지, 목록은 계속 1건).
                    본인 소유가 아니거나 경로의 boardId 와 초안의 게시판이 다르면 404 입니다.

                    실패: 400 {"message":"저장할 내용이 없습니다."}
                    실패: 404 {"message":"존재하지 않는 임시저장입니다."}
                    """)
    @PutMapping("/drafts/{draftId}")
    public ResponseEntity<DraftResponse> overwrite(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "임시저장 ID", example = "12") @PathVariable Long draftId,
            @RequestBody DraftSaveRequest request) {
        log.info("임시저장 덮어쓰기 - boardId: {}, draftId: {}", boardId, draftId);
        return ResponseEntity.ok(draftService.overwrite(boardId, draftId, request));
    }

    @Operation(summary = "내 임시저장 목록",
            description = """
                    글쓰기 화면의 '저장 | n' 목록을 그릴 때 호출합니다. 최근 저장 순으로 내려갑니다.
                    각 항목에 일반 첨부 개수(attachCnt)와 본문 미리보기(preview, 태그 제거 50자)가 포함됩니다.
                    슬롯은 회원 단위(게시판 무관)라 목록도 회원의 전체 초안을 반환합니다(각 항목이 자기 boardId 를 가집니다).

                    ### 응답 예시
                    ```json
                    {"count": 2, "drafts": [
                      {"draftId": 12, "slotNo": 1, "title": "쓰다 만 글", "preview": "본문 앞부분...", "attachCnt": 2, "updatedAt": "2026-08-17T10:00:00"}
                    ]}
                    ```
                    """)
    @GetMapping("/drafts")
    public ResponseEntity<DraftListResponse> list(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId) {
        log.info("임시저장 목록 - boardId: {}", boardId);
        return ResponseEntity.ok(draftService.list(boardId));
    }

    @Operation(summary = "임시저장 단건 불러오기",
            description = """
                    목록에서 초안을 눌러 글쓰기 화면으로 '이어쓰기' 복원할 때 호출합니다.
                    제목·본문(HTML, 이미지 포함)·카테고리·익명여부와 일반 첨부 목록을 그대로 돌려줍니다.

                    실패: 404 {"message":"존재하지 않는 임시저장입니다."} (남의 것/없음/게시판 불일치)
                    """)
    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<DraftDetailResponse> get(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "임시저장 ID", example = "12") @PathVariable Long draftId) {
        log.info("임시저장 불러오기 - boardId: {}, draftId: {}", boardId, draftId);
        return ResponseEntity.ok(draftService.get(boardId, draftId));
    }

    @Operation(summary = "임시저장 삭제",
            description = """
                    초안을 버릴 때 호출합니다. 초안에 묶인 첨부/이미지의 **물리 파일까지 함께 삭제**됩니다.

                    실패: 404 {"message":"존재하지 않는 임시저장입니다."}
                    """)
    @DeleteMapping("/drafts/{draftId}")
    public ResponseEntity<DraftResponse> delete(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "임시저장 ID", example = "12") @PathVariable Long draftId) {
        log.info("임시저장 삭제 - boardId: {}, draftId: {}", boardId, draftId);
        return ResponseEntity.ok(draftService.delete(boardId, draftId));
    }

    @Operation(summary = "본문 이미지 선업로드",
            description = """
                    리치 에디터에 이미지를 삽입하는 순간 호출합니다(multipart/form-data, 필드명 file).
                    응답의 url(/files/{attachmentId})을 <img src>로 본문에 심으면 됩니다 — 이 URL 은
                    소유자(draft/post)와 무관하게 안정적이라 임시저장·발행 후에도 깨지지 않습니다.

                    ### 응답 예시
                    ```json
                    {"attachmentId": 31, "url": "/files/31", "originName": "그림.png", "fileSize": 20480, "attachmentType": "image"}
                    ```
                    실패: 400 {"message":"이미지 파일만 업로드할 수 있습니다."}
                    """)
    @PostMapping(value = "/posts/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PreUploadResponse> uploadImage(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @RequestPart("file") MultipartFile file) {
        log.info("본문 이미지 선업로드 - boardId: {}", boardId);
        return ResponseEntity.ok(draftService.uploadImage(boardId, file));
    }

    @Operation(summary = "초안 첨부 선업로드",
            description = """
                    임시저장에 일반 첨부(pdf 등)를 붙일 때 호출합니다(multipart/form-data, 필드명 file).
                    응답의 attachmentId 를 저장/덮어쓰기 요청의 attachmentIds 에 넣으면 초안에 귀속됩니다.

                    ### 응답 예시
                    ```json
                    {"attachmentId": 32, "url": "/files/32", "originName": "자료.pdf", "fileSize": 123456, "attachmentType": "file"}
                    ```
                    """)
    @PostMapping(value = "/drafts/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PreUploadResponse> uploadAttachment(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @RequestPart("file") MultipartFile file) {
        log.info("초안 첨부 선업로드 - boardId: {}", boardId);
        return ResponseEntity.ok(draftService.uploadAttachment(boardId, file));
    }
}
