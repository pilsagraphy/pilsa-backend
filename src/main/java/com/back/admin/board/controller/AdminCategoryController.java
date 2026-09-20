package com.back.admin.board.controller;

import com.back.admin.board.dto.AdminCategoryResponse;
import com.back.admin.board.dto.CategorySaveRequest;
import com.back.admin.board.service.AdminCategoryService;
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
 * 게시판별 카테고리(태그) 관리 (시안: 관리자 > 커뮤니티 관리 > 게시판 관리).
 *
 * 카테고리는 게시판에 딸린 값이라 경로도 게시판 아래에 둔다.
 * 회원 화면의 카테고리 목록은 GET /api/user/boards/{boardId}/categories 로 따로 있다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/boards/{boardId}/categories")
@Tag(name = "관리자-게시판 카테고리 관리",
        description = "게시판별 카테고리(태그)를 등록·수정·삭제한다. 여기서 만든 카테고리는 곧바로 해당 게시판의 글쓰기·목록 필터에 나타난다. '중요'(code=PINNED)는 상단 고정 통로라 잠겨 있다.")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    @Operation(summary = "카테고리 목록 (관리자)",
            description = """
                    게시판 관리에서 카테고리를 펼칠 때 호출한다. 쓰고 있는 글 수를 함께 준다 — 삭제 가능 여부를 화면에서 미리 알 수 있다.

                    ### 요청 예시
                    ```
                    GET /api/admin/boards/2/categories
                    ```

                    ### 응답 예시
                    ```json
                    [{"categoryId":4,"name":"일상","code":"CAT_9F2A1B7C","displayOrder":1,"postCount":12,"isPinned":false},
                     {"categoryId":9,"name":"중요","code":"PINNED","displayOrder":99,"postCount":3,"isPinned":true}]
                    ```

                    isPinned=true 인 줄은 상단 고정에 쓰이므로 이름 수정·삭제가 막혀 있다(400).
                    """)
    @GetMapping
    public ResponseEntity<List<AdminCategoryResponse>> getCategories(
            @Parameter(description = "게시판 id", example = "2") @PathVariable Long boardId) {
        return ResponseEntity.ok(adminCategoryService.getCategories(boardId));
    }

    @Operation(summary = "카테고리 등록 (관리자)",
            description = """
                    게시판에 새 카테고리를 추가한다. 추가 즉시 그 게시판의 글쓰기 선택지와 목록 필터에 나타난다(재배포 불필요).

                    ### 요청 예시
                    ```json
                    {"name":"질문"}
                    ```
                    - displayOrder 를 같이 보내면 그 자리(1부터)에 끼워 넣고 나머지를 한 칸씩 민다. 생략하면 맨 뒤.
                    - 예전에 삭제한 이름을 다시 넣으면 **그 카테고리가 되살아난다** — 그 카테고리를 달고 있던
                      옛 글들의 배지도 함께 돌아온다(글의 category_id 를 지우지 않기 때문).

                    ### 응답 예시 (201 Created)
                    ```json
                    {"categoryId":12,"name":"질문","code":"CAT_9F2A1B7C","displayOrder":2,"postCount":0,"isPinned":false}
                    ```

                    실패: 400 {"message":"카테고리 이름은 필수입니다."}
                         404 {"message":"존재하지 않는 게시판입니다."}
                         409 {"message":"이미 있는 카테고리 이름입니다."}
                    """)
    @PostMapping
    public ResponseEntity<AdminCategoryResponse> createCategory(
            @Parameter(description = "게시판 id", example = "2") @PathVariable Long boardId,
            @RequestBody CategorySaveRequest request) {
        log.info("[관리자] 카테고리 생성 요청 - boardId: {}, name: {}", boardId, request.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminCategoryService.createCategory(boardId, request));
    }

    @Operation(summary = "카테고리 수정 (관리자)",
            description = """
                    이름과 노출 순서를 바꾼다. 보낸 값만 바뀐다.

                    ### 요청 예시
                    ```json
                    {"name":"공지", "displayOrder":1}
                    ```
                    displayOrder 는 **몇 번째 자리**(1부터)를 뜻한다. 서버가 나머지를 한 칸씩 밀어 1..N 을 다시 채우므로
                    프론트는 옮긴 카테고리 하나만 보내면 된다.

                    이름을 바꿔도 그 카테고리를 달고 있는 글들은 그대로 따라온다(글은 id 를 가리킨다).

                    ### 응답 예시
                    ```json
                    {"categoryId":12,"name":"공지","code":"CAT_9F2A1B7C","displayOrder":1,"postCount":4,"isPinned":false}
                    ```

                    실패: 400 {"message":"'중요'는 상단 고정에 쓰이는 카테고리라 이름을 바꿀 수 없습니다."}
                         404 {"message":"존재하지 않는 카테고리입니다."}
                         409 {"message":"이미 있는 카테고리 이름입니다."}
                    """)
    @PatchMapping("/{categoryId}")
    public ResponseEntity<AdminCategoryResponse> updateCategory(
            @Parameter(description = "게시판 id", example = "2") @PathVariable Long boardId,
            @Parameter(description = "카테고리 id", example = "12") @PathVariable Long categoryId,
            @RequestBody CategorySaveRequest request) {
        log.info("[관리자] 카테고리 수정 요청 - boardId: {}, categoryId: {}", boardId, categoryId);
        return ResponseEntity.ok(adminCategoryService.updateCategory(boardId, categoryId, request));
    }

    @Operation(summary = "카테고리 삭제 (관리자, 소프트)",
            description = """
                    카테고리를 목록에서 내린다(is_active=0). 글의 category_id 는 건드리지 않으므로,
                    같은 이름으로 다시 등록하면 되살아난다.

                    **쓰고 있는 글이 있으면 삭제할 수 없다(409).** 그냥 내리면 그 글들의 배지만 조용히 사라지기 때문에,
                    글을 먼저 다른 카테고리로 옮기게 한다. 남은 글 수는 목록 응답의 postCount 로 미리 보여 줄 수 있다.

                    ### 요청 예시
                    ```
                    PATCH /api/admin/boards/2/categories/12/delete
                    ```
                    (본문 없음)

                    ### 응답 예시
                    ```json
                    {"message":"카테고리가 삭제되었습니다."}
                    ```

                    실패: 400 {"message":"'중요'는 상단 고정에 쓰이는 카테고리라 지울 수 없습니다."}
                         409 {"message":"게시글 12건이 이 카테고리를 쓰고 있어 삭제할 수 없습니다."}
                         409 {"message":"이 게시판의 기본 카테고리라 삭제할 수 없습니다. 기본 카테고리를 먼저 바꿔 주세요."}
                    """)
    @PatchMapping("/{categoryId}/delete")
    public ResponseEntity<Map<String, String>> deleteCategory(
            @Parameter(description = "게시판 id", example = "2") @PathVariable Long boardId,
            @Parameter(description = "카테고리 id", example = "12") @PathVariable Long categoryId) {
        log.info("[관리자] 카테고리 삭제 요청 - boardId: {}, categoryId: {}", boardId, categoryId);
        adminCategoryService.deleteCategory(boardId, categoryId);
        return ResponseEntity.ok(Map.of("message", "카테고리가 삭제되었습니다."));
    }
}
