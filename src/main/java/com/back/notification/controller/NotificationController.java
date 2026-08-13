package com.back.notification.controller;

import com.back.notification.dto.NotificationPageResponse;
import com.back.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
@Tag(name = "알림", description = "헤더 종 아이콘. 로그인 회원 공통 기능(신분·관리자 여부 무관)")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록", description = "unreadOnly=true 면 안 읽은 알림만 조회합니다.")
    @GetMapping
    public ResponseEntity<NotificationPageResponse> getMyNotifications(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(notificationService.getMyNotifications(page, size, unreadOnly));
    }

    @Operation(summary = "안 읽은 알림 개수", description = "종 아이콘 뱃지용")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount()));
    }

    @Operation(summary = "알림 읽음 처리 (단건)")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(Map.of("message", "읽음 처리되었습니다."));
    }

    @Operation(summary = "알림 전체 읽음 처리")
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        int updated = notificationService.markAllAsRead();
        return ResponseEntity.ok(Map.of("message", "전체 읽음 처리되었습니다.", "updatedCount", updated));
    }

    @Operation(summary = "알림 삭제", description = "소프트 삭제됩니다.")
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long notificationId) {
        notificationService.delete(notificationId);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }
}
