package com.back.mypage.notification.controller;

import com.back.mypage.notification.dto.NotificationPageResponse;
import com.back.mypage.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/mypage/toast")
@Tag(name = "마이페이지-알림",
        description = "헤더 종 아이콘 알림. 로그인 회원 공통 기능(신분·관리자 여부 무관). "
                + "현재 실제로 발행되는 알림은 COMMENT(내 글에 댓글)/REPLY(내 댓글에 답글) 2종뿐이다.")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록",
            description = """
                    헤더 종 아이콘을 눌러 알림 목록을 펼칠 때 호출한다. unreadOnly=true 면 안 읽은 알림만 조회한다.
                    type 은 COMMENT|REPLY|REPORT_RESOLVED|SANCTION|NOTICE 로 정의되어 있으나,
                    현재 발행되는 알림은 COMMENT(내 글에 댓글)/REPLY(내 댓글에 답글) 2종뿐이다.

                    ### 요청 예시
                    ```
                    GET /api/user/mypage/toast?page=1&size=20&unreadOnly=false
                    ```

                    ### 응답 예시
                    ```json
                    {
                      "totalPages": 1, "totalCount": 3, "unreadCount": 1,
                      "notifications": [{
                        "notificationId": 12, "type": "COMMENT",
                        "title": "새 댓글이 달렸습니다.", "message": null,
                        "linkUrl": "/api/user/boards/2/posts/171", "targetType": "post", "targetId": 171,
                        "isRead": false, "createdAt": "2026-08-14T10:20:00"
                      }]
                    }
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    """)
    @GetMapping
    public ResponseEntity<NotificationPageResponse> getMyNotifications(
            @Parameter(description = "페이지 번호 (1부터)", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지당 알림 수", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "true 면 안 읽은 알림만 조회", example = "false")
            @RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(notificationService.getMyNotifications(page, size, unreadOnly));
    }

    @Operation(summary = "안 읽은 알림 개수 (뱃지)",
            description = """
                    헤더 종 아이콘의 빨간 뱃지 숫자를 그릴 때 호출한다. 페이지 진입/폴링 시 가볍게 쓰는 용도.

                    ### 요청 예시
                    ```
                    GET /api/user/mypage/toast/unread-count
                    ```

                    ### 응답 예시
                    ```json
                    {"unreadCount": 3}
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    """)
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount()));
    }

    @Operation(summary = "알림 읽음 처리 (단건)",
            description = """
                    알림 목록에서 알림 하나를 클릭해 해당 글로 이동할 때 호출한다. 본인 알림만 처리된다.

                    ### 요청 예시
                    ```
                    PATCH /api/user/mypage/toast/12/read
                    ```
                    본문 없음.

                    ### 응답 예시
                    ```json
                    {"message":"읽음 처리되었습니다."}
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    """)
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @Parameter(description = "읽음 처리할 알림 ID", example = "12")
            @PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(Map.of("message", "읽음 처리되었습니다."));
    }

    @Operation(summary = "알림 전체 읽음 처리",
            description = """
                    알림 목록 상단의 "모두 읽음" 버튼에서 호출한다. 내 미읽음 알림을 한 번에 읽음 처리한다.

                    ### 요청 예시
                    ```
                    PATCH /api/user/mypage/toast/read-all
                    ```
                    본문 없음.

                    ### 응답 예시
                    ```json
                    {"message":"전체 읽음 처리되었습니다.","updatedCount":3}
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    """)
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        int updated = notificationService.markAllAsRead();
        return ResponseEntity.ok(Map.of("message", "전체 읽음 처리되었습니다.", "updatedCount", updated));
    }

    @Operation(summary = "알림 삭제 (소프트)",
            description = """
                    알림 목록에서 알림 하나를 지울 때 호출한다. 물리 삭제가 아니라 소프트 삭제이며 목록에서만 사라진다.

                    ### 요청 예시
                    ```
                    PATCH /api/user/mypage/toast/12/delete
                    ```
                    본문 없음.

                    ### 응답 예시
                    ```json
                    {"message":"삭제되었습니다."}
                    ```

                    실패: 401 {"message":"..."} (미인증)
                    """)
    @PatchMapping("/{notificationId}/delete")
    public ResponseEntity<Map<String, String>> delete(
            @Parameter(description = "삭제할 알림 ID", example = "12")
            @PathVariable Long notificationId) {
        notificationService.delete(notificationId);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }
}
