package com.back.board.controller;

import com.back.board.dto.BoardSummaryResponse;
import com.back.board.service.BoardPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 게시판 목록 (네비게이션/사이드바용).
 *
 * 게시판이 데이터가 되었으므로 프론트가 게시판 종류를 하드코딩할 수 없다.
 * 이 API로 "현재 사용자가 볼 수 있는 게시판"만 받아서 메뉴를 그린다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/boards")
@Tag(name = "게시판(글·댓글)", description = "게시판 통합 API. boardId 로 게시판 구분(기본: 1=공지사항, 2=자유게시판, 3=정보게시판). 관리자가 추가한 게시판도 동일 경로 사용")
public class BoardListController {

    private final BoardPolicyService boardPolicyService;

    @Operation(summary = "게시판 목록 조회 (사이드바 메뉴)",
            description = """
                    사이드바/네비게이션의 게시판 메뉴를 그릴 때 호출합니다. 게시판은 관리자가 런타임에 추가하는 데이터이므로
                    프론트는 메뉴를 하드코딩하지 말고 반드시 이 API 결과로 그립니다.
                    노출 순서(displayOrder)대로, 현재 사용자의 신분·관리레벨로 열람 가능한 게시판만 반환하며
                    비로그인은 전체공개 게시판만 보입니다. canWrite 로 글쓰기 버튼 노출 여부를 판단합니다.

                    ### 요청 예시
                    ```
                    GET /api/user/boards    (쿼리 없음)
                    ```

                    ### 응답 예시
                    ```json
                    [
                      {"boardId": 1, "boardName": "공지사항", "displayOrder": 1, "canWrite": false,
                       "allowComment": true, "allowAttachment": true, "categoryMode": false,
                       "allowAnonymous": false, "allowPrivateComment": false}
                    ]
                    ```
                    ※ canWrite: 현재 사용자가 이 게시판에 글을 쓸 수 있는가 (작성 버튼 노출 판단)
                    ※ categoryMode/allowAnonymous/allowPrivateComment: 글쓰기·댓글 UI 의 옵션 노출 여부
                    """)
    @GetMapping
    public ResponseEntity<List<BoardSummaryResponse>> getBoards() {
        return ResponseEntity.ok(boardPolicyService.getReadableBoards());
    }
}
