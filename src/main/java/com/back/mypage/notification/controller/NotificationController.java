package com.back.mypage.notification.controller;

import com.back.mypage.notification.dto.NotificationListResponse;
import com.back.mypage.notification.dto.NotificationReadResponse;
import com.back.mypage.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 알림함 (헤더 종 아이콘) — 목록/뱃지/읽음(단건·전체)/삭제.
 *
 * 로그인 회원 공통(신분·관리자 무관). 수신 기기 등록/해제·VAPID 는 {@link NotificationDeviceController} 가 담당한다.
 * 현재 실제로 발행되는 알림은 COMMENT(내 글에 댓글)/REPLY(내 댓글에 답글) 2종뿐이다.
 * 남의 알림 접근은 전 쿼리의 user_id 조건(AuthUtils)으로 차단하며, 없거나 본인 것이 아니면 404.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/mypage/toast")
@Tag(name = "마이페이지-알림")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록",
            description = """
                    헤더 종 아이콘을 눌러 알림 목록을 펼칠 때 호출한다.
                    페이징 없이 최근 N개월치(policy_settings.notification_list_months, 현재 2)를 최신순 전체 반환한다.
                    각 항목의 boardId/targetType/targetId 로 프론트가 화면 경로를 조립한다(linkUrl 은 내려가지 않는다).

                    ### 응답 예시
                    ```json
                    {
                      "unreadCount": 1,
                      "toasts": [{
                        "toastId": 12, "type": "COMMENT", "title": "새 댓글이 달렸습니다.", "message": null,
                        "targetType": "post", "targetId": 171, "boardId": 2,
                        "isRead": false, "createdAt": "2026-08-14T10:20:00"
                      }]
                    }
                    ```
                    실패: 401 {"message":"..."} (미인증)
                    """)
    @GetMapping
    public ResponseEntity<NotificationListResponse> getList() {
        return ResponseEntity.ok(notificationService.getList());
    }

    @Operation(summary = "안 읽은 알림 개수 (뱃지)",
            description = """
                    헤더 종 아이콘의 뱃지 숫자를 그릴 때 호출한다. 목록과 동일한 최근 N개월 창 기준.

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
                    알림 하나를 클릭해 대상으로 이동할 때 호출한다. 본인 알림만 처리된다.
                    응답의 이동정보(type/targetType/targetId/boardId)로 목록을 다시 부르지 않고 바로 이동한다.
                    **이미 읽은 알림을 다시 호출해도 200 + 동일 응답**(멱등 — 재클릭 이동 보장).

                    ### 응답 예시
                    ```json
                    {
                      "message": "읽음 처리되었습니다.",
                      "toastId": 12, "type": "COMMENT",
                      "targetType": "post", "targetId": 171, "boardId": 2,
                      "unreadCount": 2
                    }
                    ```
                    실패: 404 {"message":"알림을 찾을 수 없습니다."} (없거나 본인 알림이 아님) / 401 (미인증)
                    """)
    @PatchMapping("/{toastId}/read")
    public ResponseEntity<NotificationReadResponse> markAsRead(
            @Parameter(description = "읽음 처리할 알림 ID", example = "12")
            @PathVariable Long toastId) {
        return ResponseEntity.ok(notificationService.markAsRead(toastId));
    }

    @Operation(summary = "알림 전체 읽음 처리",
            description = """
                    목록 상단 "모두 읽음"에서 호출한다. 최근 N개월 창의 내 미읽음 알림을 한 번에 읽음 처리한다.

                    ### 응답 예시
                    ```json
                    {"message":"전체 읽음 처리되었습니다.","updatedCount":3,"unreadCount":0}
                    ```
                    실패: 401 {"message":"..."} (미인증)
                    """)
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        return ResponseEntity.ok(notificationService.markAllAsRead());
    }

    @Operation(summary = "알림 삭제 (소프트)",
            description = """
                    알림 하나를 지울 때 호출한다. 소프트 삭제이며 목록에서만 사라진다. 대상은 경로변수로만 받는다.
                    이미 삭제됐거나 본인 알림이 아니면 404(지운 알림으로 이동할 일이 없어 멱등 처리하지 않는다).

                    ### 응답 예시
                    ```json
                    {"message":"삭제되었습니다.","toastId":12,"unreadCount":2}
                    ```
                    실패: 404 {"message":"알림을 찾을 수 없습니다."} / 401 (미인증)
                    """)
    @PatchMapping("/{toastId}/delete")
    public ResponseEntity<Map<String, Object>> delete(
            @Parameter(description = "삭제할 알림 ID", example = "12")
            @PathVariable Long toastId) {
        return ResponseEntity.ok(notificationService.delete(toastId));
    }
}
