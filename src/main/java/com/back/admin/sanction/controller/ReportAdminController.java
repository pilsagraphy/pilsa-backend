package com.back.admin.sanction.controller;

import com.back.admin.sanction.dto.BulkResultResponse;
import com.back.admin.sanction.dto.ReportBulkRequest;
import com.back.admin.sanction.dto.ReportPageResponse;
import com.back.admin.sanction.service.ReportAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 신고 관리(관리자).
 *
 * 조치는 "선택 처리(일괄)" 3종뿐이다 — 단건 조치는 targetIds 에 1건만 담아 호출한다.
 * 신고 관리 / 게시글 관리 / 댓글 관리 화면이 모두 이 API를 공유한다.
 */
@Tag(name = "관리자-신고 관리",
        description = "신고된 게시글/댓글 목록 조회와 선택 조치(복원·삭제·블라인드)를 담당한다. "
                + "신고 관리 / 게시글 관리 / 댓글 관리 화면이 모두 이 API를 공유하며, 단건 조치도 targetIds에 1건만 담아 호출한다.")
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportAdminController {

    private final ReportAdminService reportAdminService;

    // 게시글 신고 목록 (state 필터=블라인드/삭제, 미지정 시 normal 제외)
    @Operation(
            summary = "신고된 게시글 목록 (관리자)",
            description = """
                    신고관리 페이지 진입 시와 필터(상태/게시판/검색/정렬) 변경 시 호출된다.
                    동일 게시글에 대한 중복 신고는 대상 단위로 그룹핑되어 reportCount로 합산된다.
                    상태(state) 필터로 블라인드/삭제된 대상만 조회할 수 있고, 미지정 시 기본은 반려(복구)된 신고만 제외하고
                    pending(미조치)·blind·deleted 를 모두 내려준다. → 복원(복구=반려)된 대상만 이 목록에서 제외된다(신규 신고는 계속 노출).

                    ### 요청 예시
                    ```
                    GET /api/admin/reports/posts?page=1&size=10&state=blind&boardId=2&keyword=홍길동&sort=latest
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "totalPages": 1,
                      "totalCount": 4,
                      "items": [{
                        "targetType": "post", "targetId": 171, "postId": 171,
                        "preview": "본문 앞부분 30자", "boardId": 2, "boardName": "자유게시판",
                        "authorName": "홍길동", "reasonLabel": "욕설/비방",
                        "firstReportedAt": "2026-08-14T10:00:00", "reportCount": 3,
                        "state": "blind"
                      }]
                    }
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @GetMapping("/api/admin/reports/posts")
    public ResponseEntity<ReportPageResponse> getReportedPosts(
            @Parameter(description = "페이지 번호 (1부터)", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "상태 필터 (blind/deleted). 미지정 시 반려(복구)된 신고만 제외 — pending·blind·deleted 노출", example = "blind")
            @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "검색어 (본문 또는 글쓴이명 부분일치)", example = "홍길동")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "게시판 ID 필터. 미지정 시 전체 게시판", example = "2")
            @RequestParam(value = "boardId", required = false) Long boardId,
            @Parameter(description = "정렬 방식 (latest=최신순)", example = "latest")
            @RequestParam(value = "sort", defaultValue = "latest") String sort) {
        log.info("[관리자] 게시글 신고 목록 - state:{}, keyword:{}, boardId:{}, sort:{}", state, keyword, boardId, sort);
        return ResponseEntity.ok(reportAdminService.getReportedPosts(page, size, state, keyword, boardId, sort));
    }

    // 댓글 신고 목록 (state 필터=블라인드/삭제, 미지정 시 normal 제외, 내용+글쓴이 검색)
    @Operation(
            summary = "신고된 댓글 목록 (관리자)",
            description = """
                    신고관리 페이지의 댓글 탭 진입 시와 필터 변경 시 호출된다.
                    댓글은 원문으로 이동할 수 있도록 소속 게시글의 postId가 함께 내려간다.
                    동일 댓글에 대한 중복 신고는 대상 단위로 그룹핑되어 reportCount로 합산된다.
                    상태(state) 필터로 블라인드/삭제된 댓글만 조회할 수 있고, 미지정 시 기본은 반려(복구)된 신고만 제외하고
                    pending(미조치)·blind·deleted 를 모두 내려준다. → 복원(복구=반려)된 댓글만 이 목록에서 제외된다(신규 신고는 계속 노출).

                    ### 요청 예시
                    ```
                    GET /api/admin/reports/comments?page=1&size=10&state=deleted&boardId=2&keyword=홍길동&sort=latest
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "totalPages": 1,
                      "totalCount": 2,
                      "items": [{
                        "targetType": "comment", "targetId": 200, "postId": 171,
                        "preview": "댓글 앞부분", "boardId": 2, "boardName": "자유게시판",
                        "authorName": "홍길동", "reasonLabel": "광고/홍보",
                        "firstReportedAt": "2026-08-14T10:05:00", "reportCount": 1,
                        "state": "deleted"
                      }]
                    }
                    ```
                    postId = 원문 이동용 게시글 id

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @GetMapping("/api/admin/reports/comments")
    public ResponseEntity<ReportPageResponse> getReportedComments(
            @Parameter(description = "페이지 번호 (1부터)", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "상태 필터 (blind/deleted). 미지정 시 반려(복구)된 신고만 제외 — pending·blind·deleted 노출", example = "blind")
            @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "검색어 (댓글 내용 또는 글쓴이명 부분일치)", example = "홍길동")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "게시판 ID 필터. 미지정 시 전체 게시판", example = "2")
            @RequestParam(value = "boardId", required = false) Long boardId,
            @Parameter(description = "정렬 방식 (latest=최신순)", example = "latest")
            @RequestParam(value = "sort", defaultValue = "latest") String sort) {
        log.info("[관리자] 댓글 신고 목록 - state:{}, keyword:{}, boardId:{}, sort:{}", state, keyword, boardId, sort);
        return ResponseEntity.ok(reportAdminService.getReportedComments(page, size, state, keyword, boardId, sort));
    }

    // 선택 복원 (=신고 반려). 사유를 받지 않는다
    @Operation(
            summary = "신고 선택 복원 (일괄, 관리자)",
            description = """
                    신고관리/게시글관리/댓글관리 페이지에서 선택한 대상을 복원(=신고 반려)할 때 호출된다.
                    단건 조치용 API는 따로 없다 — 단건도 targetIds에 1건만 담아 호출하는 일괄 API다.
                    복원은 사유를 받지 않으며, 처리 결과로 대상별 pending 신고를 전부 rejected로 종료한다.
                    이미 삭제(deleted)된 대상은 되살리지 않는다(의도적 삭제·벌점 보호).

                    ### 요청 예시
                    ```json
                    {
                      "targetType": "post",
                      "targetIds": [171, 172]
                    }
                    ```
                    targetType: post | comment

                    ### 응답 예시 (부분 성공)
                    ```json
                    {
                      "successCount": 1,
                      "failCount": 1,
                      "failures": [{"id": 172, "message": "존재하지 않는 게시글입니다."}]
                    }
                    ```
                    항목마다 독립 트랜잭션 — 일부가 실패해도 나머지는 그대로 처리된다(부분 성공).
                    요청에 중복 id가 있으면 한 번만 처리된다.

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @PatchMapping("/api/admin/reports/select-restore")
    public ResponseEntity<BulkResultResponse> selectRestore(@RequestBody ReportBulkRequest request) {
        log.info("[관리자] 신고 선택 복원 - type:{}, count:{}", request.getTargetType(),
                request.getTargetIds() == null ? 0 : request.getTargetIds().size());
        return ResponseEntity.ok(reportAdminService.selectRestore(request.getTargetType(), request.getTargetIds()));
    }

    // 선택 삭제 (소프트 삭제 + 작성자 주의 +2)
    @Operation(
            summary = "신고 선택 삭제 (일괄, 관리자)",
            description = """
                    신고관리/게시글관리/댓글관리 페이지에서 선택한 대상을 삭제 조치할 때 호출된다.
                    단건 조치용 API는 따로 없다 — 단건도 targetIds에 1건만 담아 호출하는 일괄 API다.
                    소프트 삭제(state=deleted)와 함께 작성자에게 벌점(주의) +2가 부과되고,
                    유효 주의 누적에 따라 경고 → 정지(1주/1달/영구) 에스컬레이션이 자동 진행된다.
                    대상별 pending 신고를 resolved로 일괄 종료해 동일 대상 중복 신고로 벌점이 이중 부과되지 않는다.
                    reasonId 미전달 시 대표(최신) 신고 사유를 사용하므로 신고가 없는 게시글도 이 API로 삭제할 수 있다.

                    ### 요청 예시
                    ```json
                    {
                      "targetType": "post",
                      "targetIds": [171, 172],
                      "reasonId": 1,
                      "detail": "기타 사유일 때만"
                    }
                    ```
                    targetType: post | comment

                    ### 응답 예시 (부분 성공)
                    ```json
                    {
                      "successCount": 1,
                      "failCount": 1,
                      "failures": [{"id": 172, "message": "이미 삭제된 게시글입니다."}]
                    }
                    ```
                    항목마다 독립 트랜잭션 — 일부가 실패해도 나머지는 그대로 처리된다(부분 성공).
                    요청에 중복 id가 있으면 한 번만 처리된다.

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @PatchMapping("/api/admin/reports/select-delete")
    public ResponseEntity<BulkResultResponse> selectDelete(@RequestBody ReportBulkRequest request) {
        log.info("[관리자] 신고 선택 삭제 - type:{}, count:{}", request.getTargetType(),
                request.getTargetIds() == null ? 0 : request.getTargetIds().size());
        return ResponseEntity.ok(reportAdminService.selectDelete(
                request.getTargetType(), request.getTargetIds(), request.getReasonId(), request.getDetail()));
    }

    // 선택 블라인드 (벌점 없음)
    @Operation(
            summary = "신고 선택 블라인드 (일괄, 관리자)",
            description = """
                    신고관리/게시글관리/댓글관리 페이지에서 최종 판단 전에 대상을 임시로 가릴 때 호출된다.
                    단건 조치용 API는 따로 없다 — 단건도 targetIds에 1건만 담아 호출하는 일괄 API다.
                    state=blind로 가리기만 하며 벌점은 부과하지 않는다(삭제와의 차이).
                    최종 판단 전 임시 조치이므로 신고는 pending 상태로 남는다.

                    ### 요청 예시
                    ```json
                    {
                      "targetType": "comment",
                      "targetIds": [200, 201],
                      "reasonId": 5,
                      "detail": "기타 사유일 때만"
                    }
                    ```
                    targetType: post | comment

                    ### 응답 예시 (부분 성공)
                    ```json
                    {
                      "successCount": 2,
                      "failCount": 0,
                      "failures": []
                    }
                    ```
                    항목마다 독립 트랜잭션 — 일부가 실패해도 나머지는 그대로 처리된다(부분 성공).
                    요청에 중복 id가 있으면 한 번만 처리된다.

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @PatchMapping("/api/admin/reports/select-blind")
    public ResponseEntity<BulkResultResponse> selectBlind(@RequestBody ReportBulkRequest request) {
        log.info("[관리자] 신고 선택 블라인드 - type:{}, count:{}", request.getTargetType(),
                request.getTargetIds() == null ? 0 : request.getTargetIds().size());
        return ResponseEntity.ok(reportAdminService.selectBlind(
                request.getTargetType(), request.getTargetIds(), request.getReasonId(), request.getDetail()));
    }
}
