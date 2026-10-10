package com.back.admin.user.controller;

import com.back.admin.user.dto.UserDetailResponse;
import com.back.admin.user.mapper.UserDetailMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.mypage.notification.dto.NotificationType;
import com.back.mypage.notification.mapper.NotificationPreferenceMapper;
import com.back.mypage.notification.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 회원 상세 — 회원 목록에서 이름(아이디)을 눌러 들어오는 화면 (PM 2026-10-10).
 * 기본 정보 · 활동 · 알림(기기·유형 설정·끈 글) · 구글 연동 · 제재 요약 · 최근 30일 접속/앱 실행을 한 번에 준다.
 * 조회뿐이라 서비스 계층 없이 매퍼를 바로 쓴다. 수정·정지·탈퇴는 {@link UserController} 의 기존 API 그대로.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "관리자-회원 관리")
public class UserDetailController {

    private static final int DAYS = 30;

    private final UserDetailMapper mapper;
    private final NotificationPreferenceMapper preferenceMapper;

    @Operation(summary = "회원 상세 (관리자)", description = """
            `GET /api/admin/users/{userId}` — 탈퇴한 회원도 보인다(isDeleted=true, 개인정보는 파기돼 비어 있다).
            ```json
            { "userId": 52, "loginId": "jinsun4471", "name": "이진선", "memberType": "STUDENT", "adminLevel": 0,
              "joinedAt": "2026-03-01T10:00:00", "lastLoginAt": "2026-10-09T21:10:00", "banStatus": "none",
              "postCount": 3, "commentCount": 12, "likeGivenCount": 5, "likeReceivedCount": 9,
              "deviceCount": 1, "notificationSettings": { "COMMENT": true, "REPLY": true, "PINNED_POST": true, "EVENT": false }, "muteCount": 0,
              "googleLinked": true, "googleEmail": "a@gmail.com", "googleLinkedAt": "...", "googleLastSyncedAt": "...",
              "cautionPoints": 2, "warningCount": 0, "lastBanStartAt": null, "lastBanEndAt": null,
              "lastAccessAt": "2026-10-10T09:00:00", "accessDays30": 18, "accessDaily": [{ "date": "2026-10-10", "count": 3 }],
              "lastAppLaunchAt": "2026-10-10T08:55:00", "appLaunchDays30": 12, "appLaunchDaily": [{ "date": "2026-10-10", "count": 2 }] }
            ```
            accessDaily.count = 그 날 접속한 시간대 수(stats_access_hourly), appLaunchDaily.count = 그 날 앱 실행 횟수(app_launch_daily).
            없는 회원 404.""")
    @GetMapping("/api/admin/users/{userId}")
    @Transactional(readOnly = true)
    public ResponseEntity<UserDetailResponse> detail(@PathVariable Long userId) {
        AuthUtils.requireAdmin();
        UserDetailResponse res = mapper.findBase(userId);
        if (res == null) {
            throw new BaseException("존재하지 않는 회원입니다.", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> google = mapper.findGoogleLink(userId);
        res.setGoogleLinked(google != null);
        if (google != null) {
            res.setGoogleEmail((String) google.get("googleEmail"));
            res.setGoogleLinkedAt(toDateTime(google.get("linkedAt")));
            res.setGoogleLastSyncedAt(toDateTime(google.get("lastSyncedAt")));
        }

        Set<String> disabled = Set.copyOf(preferenceMapper.findDisabledTypes(userId));
        Map<String, Boolean> settings = new LinkedHashMap<>();
        for (NotificationType type : NotificationPreferenceService.USER_TYPES) {
            settings.put(type.name(), !disabled.contains(type.name()));
        }
        res.setNotificationSettings(settings);

        res.setAccessDaily(mapper.findAccessDaily(userId, DAYS));
        res.setAppLaunchDaily(mapper.findAppLaunchDaily(userId, DAYS));
        return ResponseEntity.ok(res);
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime dt) return dt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
