package com.back.admin.sanction.controller;

import com.back.admin.sanction.dto.ReportedCommentResponse;
import com.back.admin.sanction.dto.ReportedPostResponse;
import com.back.admin.sanction.dto.SanctionResponse;
import com.back.admin.sanction.dto.SanctionedUserDetailResponse;
import com.back.admin.sanction.dto.SanctionedUserResponse;
import com.back.admin.sanction.service.SanctionAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import com.back.global.security.AuthUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자-제재 관리",
        description = "제재(정지/영구차단/주의 누적) 중인 회원의 현황 조회와 수동 제재 해제를 담당한다. "
                + "제재회원 관리 화면에서 회원 목록 → 상세 현황 → 신고 내역 순으로 사용한다.")
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminSanctionController {

    private final SanctionAdminService sanctionAdminService;

    // 현재 제재(정지/영구차단) 중인 회원 목록
    @Operation(
            summary = "제재 회원 목록 (관리자)",
            description = """
                    제재회원목록 페이지 진입 시 호출된다.
                    현재 제재(정지/영구차단) 중이거나 주의 누적 상태인 회원을 태그와 함께 내려준다.

                    ### 요청 예시
                    ```
                    GET /api/admin/sanctions/users
                    ```

                    ### 응답 예시
                    ```json
                    [{
                      "userId": 75, "loginId": "user75", "name": "홍길동", "email": "a@b.c",
                      "banStatus": "temporary", "bannedUntil": "2026-08-21T23:59:59",
                      "banStartedAt": "2026-08-14T10:00:00", "tag": "temporary"
                    }]
                    ```
                    tag: permanent(영구차단) | temporary(기간 정지) | caution(주의 누적)

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @GetMapping("/api/admin/sanctions/users")
    public ResponseEntity<List<SanctionedUserResponse>> getSanctionedUsers() {
        log.info("제재 회원 목록 조회 요청");
        return ResponseEntity.ok(sanctionAdminService.getSanctionedUsers());
    }

    // 특정 회원의 현재 제재 현황
    @Operation(
            summary = "제재 회원 상세 현황 (관리자)",
            description = """
                    제재회원목록 페이지에서 특정 회원을 선택했을 때 상세 패널을 그리기 위해 호출된다.
                    누적 주의 진행도/경고 횟수/신고 삭제 건수를 함께 내려준다.

                    ### 요청 예시
                    ```
                    GET /api/admin/sanctions/users/75
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "tag": "temporary", "banStatus": "temporary",
                      "bannedUntil": "2026-08-21T23:59:59", "banStartedAt": "2026-08-14T10:00:00",
                      "cautionRemainder": 2, "warningCount": 1, "reportDeletedCount": 3
                    }
                    ```
                    cautionRemainder = 유효 주의 합계 % 10 (다음 경고까지의 진행도)
                    warningCount 분모는 3 (ban_policy 3단계: 1주/1달/영구)

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @GetMapping("/api/admin/sanctions/users/{userId}")
    public ResponseEntity<SanctionedUserDetailResponse> getSanctionedUserDetail(
            @Parameter(description = "조회할 회원 ID", example = "75")
            @PathVariable Long userId) {
        log.info("제재 회원 현황 조회 요청 - userId: {}", userId);
        return ResponseEntity.ok(sanctionAdminService.getSanctionedUserDetail(userId));
    }

    // 특정 회원이 받은 신고 내역 (제재회원 관리 화면3) - 게시글/댓글은 화면에 표시할 내용이 달라 경로를 나눈다
    @Operation(
            summary = "회원별 신고된 게시글 내역 (관리자)",
            description = """
                    제재회원 관리 화면(화면3)에서 해당 회원이 받은 게시글 신고 내역을 조회할 때 호출된다.
                    댓글 신고와 화면에 표시할 내용이 달라 경로가 분리되어 있다.

                    ### 요청 예시
                    ```
                    GET /api/admin/sanctions/users/85/reports/posts
                    ```

                    ### 응답 예시
                    ```json
                    [{
                      "reportId": 9, "postId": 171, "boardId": 2, "boardName": "자유게시판",
                      "title": "신고된 게시글 제목", "preview": "본문 앞 30자", "state": "normal",
                      "reasonId": 1, "reasonLabel": "욕설/비방", "detail": null,
                      "status": "resolved", "activeFlag": null,
                      "createdAt": "2026-08-14T10:00:00", "resolvedAt": "2026-08-14T11:00:00"
                    }]
                    ```
                    state는 대상 게시글의 현재 표시 상태(normal/blind/deleted)

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @GetMapping("/api/admin/sanctions/users/{userId}/reports/posts")
    public ResponseEntity<List<ReportedPostResponse>> getReportedPosts(
            @Parameter(description = "신고 내역을 조회할 회원 ID", example = "85")
            @PathVariable Long userId) {
        log.info("회원별 신고된 게시글 내역 조회 요청 - userId: {}", userId);
        return ResponseEntity.ok(sanctionAdminService.getReportedPosts(userId));
    }

    @Operation(
            summary = "회원별 신고된 댓글 내역 (관리자)",
            description = """
                    제재회원 관리 화면(화면3)에서 해당 회원이 받은 댓글 신고 내역을 조회할 때 호출된다.
                    댓글은 제목이 없고 이동 경로가 소속 게시글이라 게시글 신고와 응답 형태가 다르다
                    (원글의 postId/postTitle 이 함께 내려간다).

                    ### 요청 예시
                    ```
                    GET /api/admin/sanctions/users/85/reports/comments
                    ```

                    ### 응답 예시
                    ```json
                    [{
                      "reportId": 11, "commentId": 200, "postId": 171, "boardId": 2, "boardName": "자유게시판",
                      "postTitle": "원글 제목", "preview": "댓글 내용 앞 30자", "state": "blind",
                      "reasonId": 1, "reasonLabel": "욕설/비방", "detail": null,
                      "status": "resolved", "activeFlag": null,
                      "createdAt": "2026-08-14T10:00:00", "resolvedAt": "2026-08-14T11:00:00"
                    }]
                    ```
                    state는 대상 댓글의 현재 표시 상태(normal/blind/deleted)

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @GetMapping("/api/admin/sanctions/users/{userId}/reports/comments")
    public ResponseEntity<List<ReportedCommentResponse>> getReportedComments(
            @Parameter(description = "신고 내역을 조회할 회원 ID", example = "85")
            @PathVariable Long userId) {
        log.info("회원별 신고된 댓글 내역 조회 요청 - userId: {}", userId);
        return ResponseEntity.ok(sanctionAdminService.getReportedComments(userId));
    }

    // 관리자 수동 제재 해제
    @Operation(
            summary = "제재 수동 해제 (관리자)",
            description = """
                    제재회원 관리 화면에서 관리자가 정지/영구차단을 즉시 해제할 때 호출된다.
                    ban_status를 none으로 되돌리고 열린 ban_log를 전부 해제 처리한다(처리 관리자를 lifted_by에 기록).

                    ### 요청 예시
                    ```
                    POST /api/admin/sanctions/users/75/lift
                    ```
                    (본문 없음)

                    ### 응답 예시
                    ```json
                    {"message": "제재가 해제되었습니다."}
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    실패: 403 {"message":"..."} (관리자 권한 없음)
                    """)
    @PostMapping("/api/admin/sanctions/users/{userId}/lift")
    public ResponseEntity<SanctionResponse> liftBan(
            @Parameter(description = "제재를 해제할 회원 ID", example = "75")
            @PathVariable Long userId) {
        Long adminUserId = AuthUtils.currentUserId();
        log.info("제재 해제 요청 - 대상 userId: {}, 처리 관리자: {}", userId, adminUserId);
        sanctionAdminService.liftBan(userId, adminUserId);
        return ResponseEntity.ok(new SanctionResponse("제재가 해제되었습니다."));
    }
}
