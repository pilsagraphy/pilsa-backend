package com.back.admin.board.controller;

import com.back.admin.board.dto.AdminBoardResponse;
import com.back.admin.board.dto.BoardSaveRequest;
import com.back.admin.board.service.AdminBoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 게시판 관리 (시안: 관리자 > 커뮤니티 관리 > 게시판 관리).
 * 게시판을 런타임에 생성/수정할 수 있으며, 열람권한·작성권한도 여기서 지정한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/boards")
@Tag(name = "관리자-게시판 관리",
        description = "게시판 관리 페이지. 게시판을 런타임에 생성/수정/삭제하고 열람 권한(readScope)·작성 권한(writeLevel)·노출 순서를 설정한다. 여기서 만든 게시판은 코드 수정·재배포 없이 /api/boards/{boardId}/** 로 즉시 동작한다.")
public class AdminBoardController {

    private final AdminBoardService adminBoardService;

    @Operation(summary = "게시판 목록 (관리자)",
            description = """
                    게시판 관리 페이지 진입 시 호출한다. 전체 게시판을 게시글 수·열람권한·작성권한·노출순서와 함께 내려준다.

                    ### 요청 예시
                    ```
                    GET /api/admin/boards
                    ```
                    (쿼리 파라미터 없음)

                    ### 응답 예시
                    ```json
                    [{"boardId":2,"boardName":"자유게시판","postCount":27,
                      "readScope":"MEMBER","writeLevel":0,"displayOrder":2}]
                    ```

                    readScope: MEMBER(재학생+졸업생) | STUDENT(재학생) | ALUMNI(졸업생) — ALL 값은 없다.
                    writeLevel: 0~3 (0은 일반회원도 작성 가능, 1~3은 해당 관리레벨 이상).
                    """)
    @GetMapping
    public ResponseEntity<List<AdminBoardResponse>> getBoards() {
        return ResponseEntity.ok(adminBoardService.getBoards());
    }

    @Operation(summary = "새 게시판 생성 (관리자)",
            description = """
                    게시판 관리 페이지에서 "게시판 추가" 시 호출한다. 생성 즉시 /api/boards/{boardId}/** 가 동작한다(코드 수정 불필요).

                    ### 요청 예시
                    ```json
                    {"name":"동문 게시판","readScope":"ALUMNI","writeLevel":0}
                    ```
                    - readScope: MEMBER(재학생+졸업생) | STUDENT(재학생) | ALUMNI(졸업생) — ALL 값은 없다.
                    - writeLevel: 0~3 (0은 일반회원을 의미)

                    ### 응답 예시 (201 Created)
                    ```json
                    {"boardId":4,"boardName":"동문 게시판","postCount":0,
                     "readScope":"ALUMNI","writeLevel":0,"displayOrder":4}
                    ```

                    실패: 409 {"message":"이미 존재하는 게시판 이름입니다."}
                    """)
    @PostMapping
    public ResponseEntity<AdminBoardResponse> createBoard(@RequestBody BoardSaveRequest request) {
        log.info("[관리자] 게시판 생성 - name: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(adminBoardService.createBoard(request));
    }

    @Operation(summary = "게시판 수정 (관리자)",
            description = """
                    게시판 관리 페이지에서 게시판 이름·권한·노출순서를 변경할 때 호출한다. 전달한 필드만 수정되며,
                    수정 후 게시판 정보 전체를 반환하므로 프론트가 재조회할 필요 없다.

                    ### 요청 예시
                    ```json
                    {"name":"이름 변경","readScope":"MEMBER","writeLevel":1,"displayOrder":3}
                    ```
                    - 전달한 필드만 수정된다 (부분 수정).
                    - readScope: MEMBER(재학생+졸업생) | STUDENT(재학생) | ALUMNI(졸업생) — ALL 값은 없다. writeLevel: 0~3.

                    ### 응답 예시
                    ```json
                    {
                      "boardId":4,"boardName":"동문 게시판","postCount":12,
                      "readScope":"ALUMNI","writeLevel":0,"displayOrder":4,
                      "allowComment":true,"allowAttachment":true,"categoryMode":false,
                      "defaultCategoryId":null,"allowAnonymous":false,"allowPrivateComment":false
                    }
                    ```

                    실패: 404 {"message":"존재하지 않는 게시판입니다."}
                         409 {"message":"이미 존재하는 게시판 이름입니다."}
                         400 {"message":"열람 권한 값이 올바르지 않습니다. (MEMBER=재학+졸업 / STUDENT=재학 / ALUMNI=졸업)"}
                    """)
    @PatchMapping("/{boardId}")
    public ResponseEntity<AdminBoardResponse> updateBoard(
            @Parameter(description = "수정할 게시판 id", example = "4") @PathVariable Long boardId,
            @RequestBody BoardSaveRequest request) {
        log.info("[관리자] 게시판 수정 - boardId: {}", boardId);
        return ResponseEntity.ok(adminBoardService.updateBoard(boardId, request));
    }

    @Operation(summary = "게시판 삭제 (관리자, 소프트)",
            description = """
                    게시판 관리 페이지에서 게시판을 삭제할 때 호출한다. 소프트 삭제이며, 게시글이 남아 있는 게시판은 삭제할 수 없다(409).

                    ### 요청 예시
                    ```
                    PATCH /api/admin/boards/4/delete
                    ```
                    (본문 없음)

                    ### 응답 예시
                    ```json
                    {"message":"게시판이 삭제되었습니다."}
                    ```

                    실패: 409 {"message":"게시글이 3건 남아 있어 삭제할 수 없습니다."}
                    """)
    @PatchMapping("/{boardId}/delete")
    public ResponseEntity<Map<String, String>> deleteBoard(
            @Parameter(description = "삭제할 게시판 id", example = "4") @PathVariable Long boardId) {
        log.info("[관리자] 게시판 삭제 - boardId: {}", boardId);
        adminBoardService.deleteBoard(boardId);
        return ResponseEntity.ok(Map.of("message", "게시판이 삭제되었습니다."));
    }
}
