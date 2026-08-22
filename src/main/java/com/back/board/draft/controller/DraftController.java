package com.back.board.draft.controller;

import com.back.board.draft.dto.DraftDetailResponse;
import com.back.board.draft.dto.DraftListResponse;
import com.back.board.draft.dto.DraftRequest;
import com.back.board.draft.dto.DraftResponse;
import com.back.board.draft.service.DraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 게시판 임시저장(Draft) 컨트롤러.
 *
 * 경로는 글쓰기와 같은 /api/user/boards/{boardId} 아래 — 초안은 "게시판 글쓰기의 일부"다(SPEC-A5).
 * 게시글 등록(POST .../posts)에 draftId 를 실으면 발행과 동시에 초안이 삭제된다(BoardController 참조).
 * 접근은 전부 본인 것만 — 남의/없는/다른 게시판 초안은 404(존재 노출 방지).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/boards/{boardId}/drafts")
@Tag(name = "게시판 임시저장(Draft)", description = "글쓰기 임시저장 — 회원×게시판당 최대 5개. 발행 전 개인 작업물(물리 삭제)")
public class DraftController {

    private static final String BOARD_ID_DESC = "게시판 ID (기본 1=공지사항, 2=자유게시판, 3=정보게시판)";

    private final DraftService draftService;

    @Operation(summary = "임시저장 목록 조회",
            description = """
                    글쓰기 화면의 '저장 | N' 카운터와 임시저장 목록을 그릴 때 호출합니다. 본인 것만 내려갑니다.
                    count 필드는 없습니다 — 개수는 배열 길이(drafts.length)로 셉니다.
                    날짜는 생성일이 아니라 updatedAt(마지막 저장 시각)입니다(이어쓰기 판단 기준).

                    ### 요청 예시
                    ```
                    GET /api/user/boards/2/drafts?limit=5
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "drafts": [
                        {"draftId": 7, "title": "작성 중", "preview": "본문 앞 20자까지만", "attachCnt": 1,
                         "updatedAt": "2026-08-14T21:30:00"}
                      ]
                    }
                    ```
                    ※ preview 는 본문 앞 20자, attachCnt 는 일반 첨부 개수(본문 인라인 이미지는 제외)입니다.
                    """)
    @GetMapping
    public ResponseEntity<DraftListWrapper> getDrafts(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "가져올 개수(선택, 기본 전체)", example = "5")
            @RequestParam(value = "limit", required = false) Integer limit) {
        log.info("임시저장 목록 조회 요청 - boardId: {}, limit: {}", boardId, limit);
        List<DraftListResponse> drafts = draftService.getDrafts(boardId, limit);
        return ResponseEntity.ok(new DraftListWrapper(drafts));
    }

    @Operation(summary = "임시저장 단건 불러오기 (이어쓰기)",
            description = """
                    목록에서 초안을 선택해 글쓰기 폼에 복원할 때 호출합니다. 본인 것만 가능하며, 아니면 404입니다.
                    첨부는 일반 첨부(attachments)만 내려갑니다 — 본문 인라인 이미지는 이미 content 마크다운 안에 있습니다.

                    ### 응답 예시
                    ```json
                    {"draftId": 7, "title": "작성 중", "content": "본문", "categoryId": 4,
                     "isAnonymous": false, "updatedAt": "2026-08-14T21:30:00",
                     "attachments": [{"attachmentId": 31, "originName": "자료.pdf", "fileUrl": "/uploads/board-2/files/자료.pdf", "fileSize": 12345}]}
                    ```

                    실패: 404 {"message":"존재하지 않는 임시저장입니다."} (남의 초안·다른 게시판)
                    """)
    @GetMapping("/{draftId}")
    public ResponseEntity<DraftDetailResponse> getDraft(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "임시저장 ID", example = "7") @PathVariable Long draftId) {
        log.info("임시저장 단건 조회 요청 - boardId: {}, draftId: {}", boardId, draftId);
        return ResponseEntity.ok(draftService.getDraft(boardId, draftId));
    }

    @Operation(summary = "임시저장 생성",
            description = """
                    '글 저장하기'를 처음 누를 때 호출합니다. 빈 슬롯에 저장하고 생성된 draftId 를 돌려줍니다 —
                    프론트는 이후 저장 버튼 재클릭 시 이 draftId 로 PUT(덮어쓰기)하여 초안이 쌓이지 않게 합니다.
                    제목·내용이 둘 다 비면 400, 쓰기 권한 없는 게시판이면 403, 보관 상한(5개) 초과면 409입니다.
                    게시판 정책(익명·카테고리 유효성)은 저장 시 보정하지 않고 발행 시점에 검증합니다.

                    ### 요청 예시
                    ```json
                    {"title": "작성 중", "content": "본문", "categoryId": 4, "isAnonymous": false, "attachmentIds": [31, 32]}
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "임시저장되었습니다.", "draftId": 7}
                    ```

                    실패: 400 {"message":"제목과 내용이 모두 비어 있습니다."}
                    실패: 403 {"message":"이 게시판에 글을 등록할 권한이 없습니다."}
                    실패: 409 {"message":"임시저장은 최대 5개까지 보관할 수 있습니다. 오래된 항목을 삭제해 주세요."}
                    """)
    @PostMapping
    public ResponseEntity<DraftResponse> createDraft(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Valid @RequestBody DraftRequest request) {
        log.info("임시저장 생성 요청 - boardId: {}", boardId);
        return ResponseEntity.ok(draftService.createDraft(boardId, request));
    }

    @Operation(summary = "임시저장 덮어쓰기",
            description = """
                    프론트가 draftId 를 들고 있는 상태에서 저장 버튼을 다시 누를 때 호출합니다(초안이 쌓이지 않도록).
                    슬롯은 유지되고 updatedAt 만 갱신됩니다. 본인 것만 가능하며, 아니면 404입니다.

                    ### 요청 예시
                    ```json
                    {"title": "작성 중", "content": "본문", "categoryId": 4, "isAnonymous": false, "attachmentIds": [31, 32]}
                    ```

                    ### 응답 예시
                    ```json
                    {"message": "임시저장되었습니다."}
                    ```

                    실패: 400 {"message":"제목과 내용이 모두 비어 있습니다."}
                    실패: 404 {"message":"존재하지 않는 임시저장입니다."} (남의 초안·다른 게시판)
                    """)
    @PutMapping("/{draftId}")
    public ResponseEntity<DraftResponse> updateDraft(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "임시저장 ID", example = "7") @PathVariable Long draftId,
            @Valid @RequestBody DraftRequest request) {
        log.info("임시저장 덮어쓰기 요청 - boardId: {}, draftId: {}", boardId, draftId);
        return ResponseEntity.ok(draftService.updateDraft(boardId, draftId, request));
    }

    @Operation(summary = "임시저장 삭제",
            description = """
                    임시저장 항목을 삭제합니다(물리 삭제). 연결된 첨부의 물리 파일까지 함께 삭제됩니다.
                    본인 것만 가능하며, 아니면 404입니다.

                    ### 응답 예시
                    ```json
                    {"message": "삭제되었습니다."}
                    ```

                    실패: 404 {"message":"존재하지 않는 임시저장입니다."}
                    """)
    @DeleteMapping("/{draftId}")
    public ResponseEntity<DraftResponse> deleteDraft(
            @Parameter(description = BOARD_ID_DESC, example = "2") @PathVariable Long boardId,
            @Parameter(description = "임시저장 ID", example = "7") @PathVariable Long draftId) {
        log.info("임시저장 삭제 요청 - boardId: {}, draftId: {}", boardId, draftId);
        return ResponseEntity.ok(draftService.deleteDraft(boardId, draftId));
    }

    /**
     * 목록 응답 래퍼 — 시안 명세대로 {"drafts": [...]} 형태로 감싼다(count 필드 없음).
     */
    public record DraftListWrapper(List<DraftListResponse> drafts) {
    }
}
